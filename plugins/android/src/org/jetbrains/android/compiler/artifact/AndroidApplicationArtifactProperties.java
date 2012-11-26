package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

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
    final String artifactName = artifact.getName();
    final String messagePrefix = "[Artifact '" + artifactName + "'] ";

    final Module module = facet.getModule();
    final AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      context.addMessage(CompilerMessageCategory.ERROR, messagePrefix +
                         AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
      return;
    }
    final String sdkLocation = platform.getSdkData().getLocation();
    final String artifactFilePath = artifact.getOutputFilePath();

    final String keyStorePath = myKeyStoreUrl != null
                                ? VfsUtilCore.urlToPath(myKeyStoreUrl)
                                : "";
    final String keyStorePassword = myKeyStorePassword != null && myKeyStorePassword.length() > 0
                                    ? getPlainKeystorePassword() : null;
    final String keyPassword = myKeyPassword != null && myKeyPassword.length() > 0
                               ? getPlainKeyPassword() : null;
    try {
      final Map<AndroidCompilerMessageKind,List<String>> messages =
        AndroidCommonUtils.buildArtifact(artifactName, messagePrefix, sdkLocation, artifactFilePath,
                                         keyStorePath, myKeyAlias, keyStorePassword, keyPassword);
      AndroidCompileUtil.addMessages(context, AndroidCompileUtil.toCompilerMessageCategoryKeys(messages), null);
    }
    catch (GeneralSecurityException e) {
      AndroidCompileUtil.reportException(context, messagePrefix, e);
    }
    catch (IOException e) {
      AndroidCompileUtil.reportException(context, messagePrefix, e);
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
