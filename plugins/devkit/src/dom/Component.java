// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * plugin.dtd:component interface.
 */
public interface Component extends DomElement {

	/**
	 * Returns the list of implementation-class children.
	 * @return the list of implementation-class children.
	 */
        @NotNull
        @Required
        GenericDomValue<PsiClass> getImplementationClass();


	/**
	 * Returns the list of interface-class children.
	 * @return the list of interface-class children.
	 */
	@NotNull
	GenericDomValue<PsiClass> getInterfaceClass();


	/**
	 * Returns the list of option children.
	 * @return the list of option children.
	 */
	@NotNull
	List<Option> getOptions();
	/**
	 * Adds new child to the list of option children.
	 * @return created child
	 */
	Option addOption();


}
