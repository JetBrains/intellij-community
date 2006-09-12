package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko, lvo
 */
public class AntDefaultNSProvider implements XmlFileNSInfoProvider, ApplicationComponent {
  @NonNls
  private static final String ANT_URI = "http://ant.apache.org/schema.xsd"; // XmlUtil.ANT_URI
  private static final String[][] myNamespaces = new String[][]{new String[]{"", ANT_URI}};

  // nsPrefix, namespaceId
  public String[][] getDefaultNamespaces(@NotNull XmlFile xmlFile) {
    if (xmlFile.getCopyableUserData(XmlFile.ANT_BUILD_FILE) != null) return myNamespaces;
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && AntFileImpl.PROJECT_TAG.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue(AntFileImpl.DEFAULT_ATTR) != null) {
          return myNamespaces;
        }
        final VirtualFile file = xmlFile.getVirtualFile();
        if (file != null && file.getUserData(AntLanguageExtension.ANT_FILE_SIGN) != null) {
          return myNamespaces;
        }
      }
    }
    return null;
  }

  @NonNls
  public String getComponentName() {
    return "AntSupport.DefaultNSInfoProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static String getDefaultUri() {
    return ANT_URI;
  }
}
