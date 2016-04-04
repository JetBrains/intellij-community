package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.RootTagFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import org.jetbrains.annotations.NotNull;

public class JavaFxNamespaceDataProvider implements XmlFileNSInfoProvider, MetaDataContributor {
  public static final String JAVAFX_NAMESPACE = "http://javafx.com/fxml";

  private static final String[][] NAMESPACES = {{"", JAVAFX_NAMESPACE}};

  public String[][] getDefaultNamespaces(@NotNull XmlFile file) {
    return JavaFxFileTypeFactory.isFxml(file) ? NAMESPACES : null;
  }

  @Override
  public boolean overrideNamespaceFromDocType(@NotNull XmlFile file) {
    return false;
  }

  @Override
  public void contributeMetaData(MetaDataRegistrar registrar) {
    registrar.registerMetaData(new RootTagFilter(new NamespaceFilter(JAVAFX_NAMESPACE)), JavaFxNamespaceDescriptor.class);
  }
}
