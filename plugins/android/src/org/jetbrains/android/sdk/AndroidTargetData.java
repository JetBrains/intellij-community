package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  private final AndroidSdk mySdk;
  private final IAndroidTarget myTarget;

  private volatile AttributeDefinitions myAttrDefs;
  private volatile RenderServiceFactory myRenderServiceFactory;
  private volatile Set<String> myThemes;

  public AndroidTargetData(@NotNull AndroidSdk sdk, @NotNull IAndroidTarget target) {
    mySdk = sdk;
    myTarget = target;
  }

  @Nullable
  public AttributeDefinitions getAttrDefs(@NotNull final Project project) {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final String attrsPath = myTarget.getPath(IAndroidTarget.ATTRIBUTES);
          final String attrsManifestPath = myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES);
          final XmlFile[] files = findXmlFiles(project, attrsPath, attrsManifestPath);
          if (files != null) {
            myAttrDefs = new AttributeDefinitions(files);
          }
        }
      });
    }
    return myAttrDefs;
  }

  @Nullable
  public RenderServiceFactory getRenderServiceFactory(@NotNull Project project) throws RenderingException, IOException {
    if (myRenderServiceFactory == null) {
      final AttributeDefinitions attrDefs = getAttrDefs(project);
      if (attrDefs == null) {
        return null;
      }
      myRenderServiceFactory = RenderServiceFactory.create(myTarget, attrDefs.getEnumMap());
    }
    return myRenderServiceFactory;
  }

  public boolean areThemesCached() {
    return myThemes != null;
  }

  @NotNull
  public Set<String> getThemes(@NotNull final AndroidFacet facet) {
    if (myThemes == null) {
      myThemes = new HashSet<String>();
      final Module module = facet.getModule();
      final SystemResourceManager systemResourceManager = new SystemResourceManager(facet, new AndroidPlatform(mySdk, myTarget));

      for (VirtualFile valueResourceDir : systemResourceManager.getResourceSubdirs("values")) {
        for (final VirtualFile valueResourceFile : valueResourceDir.getChildren()) {
          if (!valueResourceFile.isDirectory() && valueResourceFile.getFileType().equals(StdFileTypes.XML)) {
            
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                final Project project = module.getProject();

                if (module.isDisposed() || project.isDisposed()) {
                  return;
                }
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiFile psiFile = psiManager.findFile(valueResourceFile);
                
                if (psiFile instanceof XmlFile) {
                  psiFile.accept(new XmlRecursiveElementVisitor() {
                    @Override
                    public void visitXmlTag(XmlTag tag) {
                      super.visitXmlTag(tag);
                      
                      if ("style".equals(tag.getName())) {
                        final String styleName = tag.getAttributeValue("name");

                        if (styleName != null && (styleName.equals("Theme") || styleName.startsWith("Theme."))) {
                          myThemes.add(styleName);
                        }
                      }
                    }
                  });
  
                  psiManager.dropResolveCaches();
                  InjectedLanguageManager.getInstance(project).dropFileCaches(psiFile);
                }
              }
            });
          }
        }
      }
    }
    return myThemes;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @Nullable
  private static XmlFile[] findXmlFiles(final Project project, final String... paths) {
    XmlFile[] xmlFiles = new XmlFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      String path = paths[i];
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      PsiFile psiFile = file != null ? ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
        @Nullable
        public PsiFile compute() {
          return PsiManager.getInstance(project).findFile(file);
        }
      }) : null;
      if (psiFile == null) {
        LOG.info("File " + path + " is not found");
        return null;
      }
      if (!(psiFile instanceof XmlFile)) {
        LOG.info("File " + path + "  is not an xml psiFile");
        return null;
      }
      xmlFiles[i] = (XmlFile)psiFile;
    }
    return xmlFiles;
  }
}
