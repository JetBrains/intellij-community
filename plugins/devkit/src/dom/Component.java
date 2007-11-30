// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.SubTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * plugin.dtd:component interface.
 */
public interface Component extends DomElement {

  @NotNull
  @Required
  GenericDomValue<PsiClass> getImplementationClass();


  @NotNull
  GenericDomValue<PsiClass> getInterfaceClass();


  @NotNull
  List<Option> getOptions();

  Option addOption();


  @NotNull
  @SubTag(value = "skipForDummyProject", indicator = true)
  GenericDomValue<Boolean> getSkipForDummyProject();
}
