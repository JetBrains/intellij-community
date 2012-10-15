package org.jetbrains.android.sdk;

import com.android.AndroidConstants;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.FilteredAttributeDefinitions;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  private volatile AttributeDefinitionsImpl myAttrDefs;
  private volatile RenderServiceFactory myRenderServiceFactory;
  private volatile Set<String> myThemes;
  private volatile boolean myThemesLoaded;

  private final Object myPublicResourceCacheLock = new Object();
  private volatile Map<String, Set<String>> myPublicResourceCache;

  public AndroidTargetData(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  @Nullable
  public AttributeDefinitions getAttrDefs(@NotNull Project project) {
    final AttributeDefinitionsImpl attrDefs = getAttrDefsImpl(project);
    return attrDefs != null ? new PublicAttributeDefinitions(attrDefs) : null;
  }

  @Nullable
  private AttributeDefinitionsImpl getAttrDefsImpl(@NotNull final Project project) {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final String attrsPath = myTarget.getPath(IAndroidTarget.ATTRIBUTES);
          final String attrsManifestPath = myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES);
          final XmlFile[] files = findXmlFiles(project, attrsPath, attrsManifestPath);
          if (files != null) {
            myAttrDefs = new AttributeDefinitionsImpl(files);
          }
        }
      });
    }
    return myAttrDefs;
  }

  @Nullable
  private Map<String, Set<String>> getPublicResourceCache() {
    synchronized (myPublicResourceCacheLock) {
      if (myPublicResourceCache == null) {
        myPublicResourceCache = parsePublicResCache();
      }
      return myPublicResourceCache;
    }
  }

  public boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    final Map<String, Set<String>> publicResourceCache = getPublicResourceCache();

    if (publicResourceCache == null) {
      return false;
    }
    final Set<String> set = publicResourceCache.get(type);
    return set != null && set.contains(name);
  }

  @Nullable
  private Map<String, Set<String>> parsePublicResCache() {
    final String resDirPath = myTarget.getPath(IAndroidTarget.RESOURCES);
    final String publicXmlPath = resDirPath + '/' + AndroidConstants.FD_RES_VALUES + "/public.xml";
    final VirtualFile publicXml = LocalFileSystem.getInstance().findFileByPath(
      FileUtil.toSystemIndependentName(publicXmlPath));

    if (publicXml != null) {
      try {
        final MyPublicResourceCacheBuilder builder = new MyPublicResourceCacheBuilder();
        NanoXmlUtil.parse(publicXml.getInputStream(), builder);
        myPublicResourceCache = builder.getPublicResourceCache();
        return myPublicResourceCache;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Nullable
  public RenderServiceFactory getRenderServiceFactory(@NotNull Project project) throws RenderingException, IOException {
    if (myRenderServiceFactory == null) {
      final AttributeDefinitionsImpl attrDefs = getAttrDefsImpl(project);
      if (attrDefs == null) {
        return null;
      }
      myRenderServiceFactory = RenderServiceFactory.create(myTarget, attrDefs.getEnumMap());
    }
    return myRenderServiceFactory;
  }

  public boolean areThemesCached() {
    return myThemesLoaded;
  }

  @NotNull
  public synchronized Set<String> getThemes(@NotNull final AndroidFacet facet) {
    if (myThemes == null) {
      myThemes = new HashSet<String>();
      final Module module = facet.getModule();
      final SystemResourceManager systemResourceManager = new SystemResourceManager(facet, new AndroidPlatform(mySdkData, myTarget));

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
                      
                      if (ResourceType.STYLE.getName().equals(tag.getName())) {
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

      myThemesLoaded = true;
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

  private class PublicAttributeDefinitions extends FilteredAttributeDefinitions {
    protected PublicAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
      super(wrappee);
    }

    @Override
    protected boolean isAttributeAcceptable(@NotNull String name) {
      return isResourcePublic(ResourceType.ATTR.getName(), name);
    }
  }

  private static class MyPublicResourceCacheBuilder extends NanoXmlUtil.IXMLBuilderAdapter {
    private final Map<String, Set<String>> myResult = new HashMap<String, Set<String>>();

    private String myName;
    private String myType;

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
      if ("public".equals(name) && myName != null && myType != null) {
        Set<String> set = myResult.get(myType);

        if (set == null) {
          set = new HashSet<String>();
          myResult.put(myType, set);
        }
        set.add(myName);
      }
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
      throws Exception {
      if ("name".equals(key)) {
        myName = value;
      }
      else if ("type".endsWith(key)) {
        myType = value;
      }
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
      throws Exception {
      myName = null;
      myType = null;
    }

    public Map<String, Set<String>> getPublicResourceCache() {
      return myResult;
    }
  }
}
