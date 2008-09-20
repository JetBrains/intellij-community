// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
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
  @ExtendClass(instantiatable = false)
  GenericDomValue<PsiClass> getInterfaceClass();

  @NotNull
  GenericDomValue<PsiClass> getHeadlessImplementationClass();

  @NotNull
  List<Option> getOptions();

  Option addOption();

  interface Application extends Component {
    @NotNull
    @Required
    GenericDomValue<PsiClass> getImplementationClass();
  }

  interface Module extends Component {
    @NotNull
    @Required
    GenericDomValue<PsiClass> getImplementationClass();
  }

  interface Project extends Component {
    @NotNull
    @Required
    GenericDomValue<PsiClass> getImplementationClass();

    @NotNull
    @SubTag(value = "skipForDummyProject", indicator = true)
    GenericDomValue<Boolean> getSkipForDummyProject();

    @NotNull
    @SubTag(value = "skipForDefaultProject", indicator = true)
    GenericDomValue<Boolean> getSkipForDefaultProject();
  }
}
