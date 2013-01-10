package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.RootTagFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;

/**
 * User: anna
 * Date: 1/9/13
 */
public class JavaFXMetaDataContributor implements MetaDataContributor {
  @Override
  public void contributeMetaData(MetaDataRegistrar registrar) {
    MetaDataRegistrar.getInstance().registerMetaData(new RootTagFilter(new NamespaceFilter(JavaFXNamespaceProvider.JAVAFX_NAMESPACE)), JavaFXNSDescriptor.class);
  }
}
