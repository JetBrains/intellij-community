package org.jetbrains.android.compiler;

import com.android.sdklib.internal.build.BuildConfigGenerator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildConfigGeneratingCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidBuildConfigGeneratingCompiler");
  private static final String MANIFEST_LOCATION = NanoXmlUtil.createLocation("manifest");

  @Nullable
  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null;
  }

  @Override
  public GenerationItem[] getGenerationItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GenerationItem[]>() {
      @Override
      public GenerationItem[] compute() {
        final List<GenerationItem> result = new ArrayList<GenerationItem>();

        for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null || AndroidCompileUtil.isLibraryWithBadCircularDependency(facet)) {
            continue;
          }

          final VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
          if (manifestFile == null) {
            context.addMessage(CompilerMessageCategory.ERROR,
                               AndroidBundle.message("android.compilation.error.manifest.not.found", module.getName()), null, -1, -1);
            continue;
          }

          String packageName;
          try {
            // we cannot use DOM here because custom manifest file can be excluded (ex. it can be located in /target/ folder)
            packageName = parsePackageName(manifestFile);
          }
          catch (IOException e) {
            context.addMessage(CompilerMessageCategory.ERROR, "I/O error: " + e.getMessage(), null, -1, -1);
            continue;
          }

          if (packageName != null) {
            packageName = packageName.trim();
          }
          if (packageName == null || packageName.length() <= 0) {
            context.addMessage(CompilerMessageCategory.ERROR, AndroidBundle.message("package.not.found.error"), manifestFile.getUrl(),
                               -1, -1);
            continue;
          }
          final boolean debug = !AndroidCompileUtil.isReleaseBuild(context);
          result.add(new MyGenerationItem(module, packageName, debug));

          for (String libPackage : AndroidCompileUtil.getLibPackages(module, packageName)) {
            result.add(new MyGenerationItem(module, libPackage, debug));
          }
        }
        return result.toArray(new GenerationItem[result.size()]);
      }
    });
  }

  @Override
  public GenerationItem[] generate(CompileContext context,
                                   GenerationItem[] items,
                                   VirtualFile outputRootDirectory) {
    if (items == null || items.length == 0) {
      return new GenerationItem[0];
    }
    context.getProgressIndicator().setText("Generating BuildConfig.java...");

    final String genFolderOsPath = FileUtil.toSystemDependentName(outputRootDirectory.getPath());
    final List<GenerationItem> result = new ArrayList<GenerationItem>();

    for (GenerationItem item : items) {
      final MyGenerationItem genItem = (MyGenerationItem)item;
      final BuildConfigGenerator generator = new BuildConfigGenerator(genFolderOsPath, genItem.myPackage, genItem.myDebug);
      try {
        generator.generate();
        result.add(genItem);
      }
      catch (IOException e) {
        context.addMessage(CompilerMessageCategory.ERROR, "I/O error: " + e.getMessage(), null, -1, -1);
        LOG.info(e);
      }
    }

    if (result.size() > 0) {
      AndroidCompileUtil.markDirtyAndRefresh(outputRootDirectory, true);
    }
    return result.toArray(new GenerationItem[result.size()]);
  }

  @Nullable
  private static String parsePackageName(@NotNull VirtualFile manifestFile) throws IOException {
    final Ref<String> packageNameRef = Ref.create(null);

    NanoXmlUtil.parse(manifestFile.getInputStream(), new NanoXmlUtil.BaseXmlBuilder() {
      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
        throws Exception {
        super.addAttribute(key, nsPrefix, nsURI, value, type);

        if (AndroidCommonUtils.PACKAGE_MANIFEST_ATTRIBUTE.equals(key) &&
            MANIFEST_LOCATION.equals(getLocation())) {
          packageNameRef.set(value);
          stop();
        }
      }

      @Override
      public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
        super.elementAttributesProcessed(name, nsPrefix, nsURI);
        stop();
      }
    });
    return packageNameRef.get();
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android BuildConfig Generator";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static class MyGenerationItem implements GenerationItem {
    final Module myModule;
    final String myPackage;
    final boolean myDebug;

    private MyGenerationItem(@NotNull Module module, @NotNull String aPackage, boolean debug) {
      myModule = module;
      myPackage = aPackage;
      myDebug = debug;
    }

    @Override
    public String getPath() {
      return myPackage.replace('.', '/') + '/' + BuildConfigGenerator.BUILD_CONFIG_NAME;
    }

    @Override
    public ValidityState getValidityState() {
      return new MyValidityState(myPackage, myDebug);
    }

    @Override
    public Module getModule() {
      return myModule;
    }

    @Override
    public boolean isTestSource() {
      return false;
    }
  }

  private static class MyValidityState implements ValidityState {
    private final String myPackage;
    private boolean myDebug;

    private MyValidityState(DataInput in) throws IOException {
      myPackage = in.readUTF();
      myDebug = in.readBoolean();
    }

    private MyValidityState(@NotNull String aPackage, boolean debug) {
      myPackage = aPackage;
      myDebug = debug;
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      final MyValidityState otherState1 = (MyValidityState)otherState;
      return otherState1.myPackage.equals(myPackage) && otherState1.myDebug == myDebug;
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeUTF(myPackage);
      out.writeBoolean(myDebug);
    }
  }
}