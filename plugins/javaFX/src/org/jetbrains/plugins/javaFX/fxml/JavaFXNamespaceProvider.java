package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import org.jetbrains.annotations.NotNull;

public class JavaFXNamespaceProvider implements XmlFileNSInfoProvider {
  public static final String JAVAFX_NAMESPACE = "http://javafx.com/fxml";

  private static final String[][] NAMESPACES = {{"", JAVAFX_NAMESPACE}};

  public String[][] getDefaultNamespaces(@NotNull XmlFile file) {
    return JavaFxFileTypeFactory.isFxml(file) ? NAMESPACES : null;
  }

  @Override
  public boolean overrideNamespaceFromDocType(@NotNull XmlFile file) {
    return false;
  }
}
