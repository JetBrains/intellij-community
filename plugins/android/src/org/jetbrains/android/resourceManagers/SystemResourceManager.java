package org.jetbrains.android.resourceManagers;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author coyote
 */
public class SystemResourceManager extends ResourceManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.resourceManagers.SystemResourceManager");
  private volatile AttributeDefinitions definitions;
  private volatile Map<String, List<PsiElement>> myIdMap;

  private final IAndroidTarget myTarget;

  public SystemResourceManager(@NotNull Module module, @NotNull IAndroidTarget target) {
    super(module);
    myTarget = target;
  }

  @NotNull
  public VirtualFile[] getAllResourceDirs() {
    VirtualFile resDir = getResourceDir();
    return resDir != null ? new VirtualFile[]{resDir} : VirtualFile.EMPTY_ARRAY;
  }

  @Nullable
  public VirtualFile getResourceDir() {
    String resPath = myTarget.getPath(IAndroidTarget.RESOURCES);
    return LocalFileSystem.getInstance().findFileByPath(resPath);
  }

  @Nullable
  public static SystemResourceManager getInstance(@NotNull ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    return facet != null ? facet.getSystemResourceManager() : null;
  }

  @Nullable
  public synchronized AttributeDefinitions getAttributeDefinitions() {
    if (definitions == null) {
      String attrsPath = myTarget.getPath(IAndroidTarget.ATTRIBUTES);
      String attrsManifestPath = myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES);
      XmlFile[] files = findFiles(attrsPath, attrsManifestPath);
      if (files != null) {
        definitions = new AttributeDefinitions(files);
      }
    }
    return definitions;
  }

  @Nullable
  public List<PsiElement> findIdDeclarations(@NotNull String id) {
    if (myIdMap == null) {
      myIdMap = createIdMap();
    }
    return myIdMap.get(id);
  }

  @NotNull
  public Collection<String> getIds() {
    if (myIdMap == null) {
      myIdMap = createIdMap();
    }
    return myIdMap.keySet();
  }

  @Nullable
  private XmlFile[] findFiles(final String... paths) {
    XmlFile[] xmlFiles = new XmlFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      String path = paths[i];
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      PsiFile psiFile = file != null ? ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
        @Nullable
        public PsiFile compute() {
          return PsiManager.getInstance(myModule.getProject()).findFile(file);
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

  @NotNull
  public Map<String, List<PsiElement>> createIdMap() {
    Map<String, List<PsiElement>> result = new HashMap<String, List<PsiElement>>();
    fillIdMap(result);
    return result;
  }

  protected void fillIdMap(@NotNull Map<String, List<PsiElement>> result) {
    for (String resType : AndroidIdIndex.RES_TYPES_CONTAINING_ID_DECLARATIONS) {
      List<PsiFile> resFiles = findResourceFiles(resType);
      for (PsiFile resFile : resFiles) {
        collectIdDeclarations(resFile, result);
      }
    }
  }

  protected static void collectIdDeclarations(PsiFile psiFile, Map<String, List<PsiElement>> result) {
    if (psiFile instanceof XmlFile) {
      XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document != null) {
        XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          fillMapRecursively(rootTag, result);
        }
      }
    }
  }

  private static void fillMapRecursively(@NotNull XmlTag tag, Map<String, List<PsiElement>> result) {
    XmlAttribute idAttr = tag.getAttribute("id", SdkConstants.NS_RESOURCES);
    if (idAttr != null) {
      XmlAttributeValue idAttrValue = idAttr.getValueElement();
      if (idAttrValue != null) {
        if (AndroidResourceUtil.isIdDeclaration(idAttrValue)) {
          String id = AndroidResourceUtil.getResourceNameByReferenceText(idAttrValue.getValue());
          if (id != null) {
            List<PsiElement> list = result.get(id);
            if (list == null) {
              list = new ArrayList<PsiElement>();
              result.put(id, list);
            }
            list.add(idAttrValue);
          }
        }
      }
    }
    for (XmlTag subtag : tag.getSubTags()) {
      fillMapRecursively(subtag, result);
    }
  }
}
