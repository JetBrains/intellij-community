package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GroovyScriptTypeEP extends AbstractExtensionPointBean {

  @Attribute("extensions")
  public String extensions;

  @Attribute("descriptorClass")
  public String descriptorClass;

  private final AtomicNotNullLazyValue<GroovyScriptType> myInstance = new AtomicNotNullLazyValue<GroovyScriptType>() {
    @NotNull
    @Override
    protected GroovyScriptType compute() {
      try {
        return instantiate(descriptorClass, ApplicationManager.getApplication().getPicoContainer());
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public GroovyScriptType getTypeDescriptor() {
    return myInstance.getValue();
  }
}
