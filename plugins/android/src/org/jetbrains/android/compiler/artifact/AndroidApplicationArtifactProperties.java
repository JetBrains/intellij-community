package org.jetbrains.android.compiler.artifact;

import com.android.sdklib.SdkConstants;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("UnusedDeclaration")
public class AndroidApplicationArtifactProperties extends ArtifactProperties<AndroidApplicationArtifactProperties> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactProperties");

  private AndroidArtifactSigningMode mySigningMode = AndroidArtifactSigningMode.RELEASE_UNSIGNED;
  private String myKeyStoreUrl = "";
  private String myKeyStorePassword = "";
  private String myKeyAlias = "";
  private String myKeyPassword = "";

  private boolean myRunProGuard;
  private String myProGuardCfgFileUrl = "";
  private boolean myIncludeSystemProGuardCfgFile;

  @Override
  public void onBuildFinished(@NotNull Artifact artifact, @NotNull CompileContext context) {
    if (!(artifact.getArtifactType() instanceof AndroidApplicationArtifactType) ||
        mySigningMode != AndroidArtifactSigningMode.RELEASE_SIGNED) {
      return;
    }

    final AndroidFacet facet = AndroidArtifactUtil.getPackagedFacet(context.getProject(), artifact);
    if (facet == null) {
      return;
    }
    final String messagePrefix = "[Artifact '" + artifact.getName() + "'] ";

    final Module module = facet.getModule();
    final AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      context.addMessage(CompilerMessageCategory.ERROR, messagePrefix +
                         AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
      return;
    }
    final Pair<PrivateKey, X509Certificate> pair = getPrivateKeyAndCertificate(context, messagePrefix);
    if (pair == null) {
      return;
    }

    final String artifactFilePath = artifact.getOutputFilePath();
    final String prefix = "Cannot sign artifact " + artifact.getName() + ": ";

    if (artifactFilePath == null) {
      context.addMessage(CompilerMessageCategory.ERROR, prefix + "output path is not specified", null, -1, -1);
      return;
    }

    final File artifactFile = new File(artifactFilePath);
    if (!artifactFile.exists()) {
      context.addMessage(CompilerMessageCategory.ERROR, prefix + "file " + artifactFilePath + " hasn't been generated", null, -1, -1);
      return;
    }

    final String zipAlignPath =
      FileUtil.toSystemDependentName(platform.getSdkData().getLocation() + '/' + AndroidCommonUtils.toolPath(SdkConstants.FN_ZIPALIGN));
    final boolean runZipAlign = new File(zipAlignPath).isFile();

    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("android_artifact", "tmp");
      final File tmpArtifact = new File(tmpDir, "tmpArtifact.apk");

      if (runZipAlign) {
        final String errorMessage = AndroidArtifactUtil.executeZipAlign(zipAlignPath, artifactFile, tmpArtifact);
        if (errorMessage != null) {
          context.addMessage(CompilerMessageCategory.ERROR, messagePrefix + "zip-align: " + errorMessage, null, -1, -1);
          return;
        }
      }
      else {
        context.addMessage(CompilerMessageCategory.WARNING, messagePrefix + AndroidBundle.message("cannot.find.zip.align"), null, -1, -1);
        FileUtil.copy(artifactFile, tmpArtifact);
      }

      if (!FileUtil.delete(artifactFile)) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot delete file " + artifactFile.getPath(), null, -1, -1);
        return;
      }
      AndroidCommonUtils.signApk(tmpArtifact, artifactFile, pair.getFirst(), pair.getSecond());
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, prefix + "I/O error: " + e.getMessage(), null, -1, -1);
    }
    catch (GeneralSecurityException e) {
      AndroidCompileUtil.reportException(context, prefix, e);
    }
    finally {
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
    }
  }

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new AndroidArtifactPropertiesEditor(context.getArtifact(), this, context.getProject());
  }

  @Override
  public AndroidApplicationArtifactProperties getState() {
    return this;
  }

  @Override
  public void loadState(AndroidApplicationArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nullable
  private Pair<PrivateKey, X509Certificate> getPrivateKeyAndCertificate(@NotNull CompileContext context, @NotNull String errorPrefix) {
    final String keyStoreFilePath = myKeyStoreUrl != null ? VfsUtilCore.urlToPath(myKeyStoreUrl) : "";

    if (keyStoreFilePath.length() == 0) {
      context.addMessage(CompilerMessageCategory.ERROR, errorPrefix + "Key store file is not specified", null, -1, -1);
      return null;
    }
    if (myKeyStorePassword == null || myKeyStorePassword.length() == 0) {
      context.addMessage(CompilerMessageCategory.ERROR, errorPrefix + "Key store password is not specified", null, -1, -1);
      return null;
    }
    if (myKeyAlias == null || myKeyAlias.length() == 0) {
      context.addMessage(CompilerMessageCategory.ERROR, errorPrefix + "Key alias is not specified", null, -1, -1);
      return null;
    }
    if (myKeyPassword == null || myKeyPassword.length() == 0) {
      context.addMessage(CompilerMessageCategory.ERROR, errorPrefix + "Key password is not specified", null, -1, -1);
      return null;
    }
    final File keyStoreFile = new File(keyStoreFilePath);
    final String keystorePasswordStr = getPlainKeystorePassword();
    final char[] keystorePassword = keystorePasswordStr.toCharArray();

    final String keyPasswordStr = getPlainKeyPassword();
    final char[] keyPassword = keyPasswordStr.toCharArray();

    final KeyStore keyStore;
    InputStream is = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      is = new FileInputStream(keyStoreFile);
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(is, keystorePassword);

      final KeyStore.PrivateKeyEntry entry =
        (KeyStore.PrivateKeyEntry)keyStore.getEntry(myKeyAlias, new KeyStore.PasswordProtection(keyPassword));
      if (entry == null) {
        context.addMessage(CompilerMessageCategory.ERROR,
                           errorPrefix + AndroidBundle.message("android.extract.package.cannot.find.key.error", myKeyAlias), null, -1, -1);
        return null;
      }

      final PrivateKey privateKey = entry.getPrivateKey();
      final Certificate certificate = entry.getCertificate();
      if (privateKey == null || certificate == null) {
        context.addMessage(CompilerMessageCategory.ERROR,
                           errorPrefix + AndroidBundle.message("android.extract.package.cannot.find.key.error", myKeyAlias), null, -1, -1);
        return null;
      }
      return Pair.create(privateKey, (X509Certificate)certificate);
    }
    catch (FileNotFoundException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    catch (KeyStoreException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    catch (CertificateException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    catch (NoSuchAlgorithmException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    catch (IOException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    catch (UnrecoverableEntryException e) {
      return AndroidCompileUtil.handleExceptionError(context, errorPrefix, e);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  public AndroidArtifactSigningMode getSigningMode() {
    return mySigningMode;
  }

  public void setSigningMode(AndroidArtifactSigningMode signingMode) {
    mySigningMode = signingMode;
  }

  public String getKeyStoreUrl() {
    return myKeyStoreUrl;
  }

  public String getKeyStorePassword() {
    return myKeyStorePassword;
  }

  public String getKeyAlias() {
    return myKeyAlias;
  }

  public String getKeyPassword() {
    return myKeyPassword;
  }

  public void setKeyStoreUrl(String keyStoreUrl) {
    myKeyStoreUrl = keyStoreUrl;
  }

  public void setKeyStorePassword(String keyStorePassword) {
    myKeyStorePassword = keyStorePassword;
  }

  public void setKeyAlias(String keyAlias) {
    myKeyAlias = keyAlias;
  }

  public void setKeyPassword(String keyPassword) {
    myKeyPassword = keyPassword;
  }

  @Transient
  @NotNull
  public String getPlainKeystorePassword() {
    return new String(new Base64().decode(myKeyStorePassword.getBytes()));
  }

  @Transient
  public void setPlainKeystorePassword(@NotNull String password) {
    myKeyStorePassword = new String(new Base64().encode(password.getBytes()));
  }

  @Transient
  @NotNull
  public String getPlainKeyPassword() {
    return new String(new Base64().decode(myKeyPassword.getBytes()));
  }

  @Transient
  public void setPlainKeyPassword(@NotNull String password) {
    myKeyPassword = new String(new Base64().encode(password.getBytes()));
  }

  public boolean isRunProGuard() {
    return myRunProGuard;
  }

  public String getProGuardCfgFileUrl() {
    return myProGuardCfgFileUrl;
  }

  public boolean isIncludeSystemProGuardCfgFile() {
    return myIncludeSystemProGuardCfgFile;
  }

  public void setRunProGuard(boolean runProGuard) {
    myRunProGuard = runProGuard;
  }

  public void setProGuardCfgFileUrl(String proGuardCfgFileUrl) {
    myProGuardCfgFileUrl = proGuardCfgFileUrl;
  }

  public void setIncludeSystemProGuardCfgFile(boolean includeSystemProGuardCfgFile) {
    myIncludeSystemProGuardCfgFile = includeSystemProGuardCfgFile;
  }
}
