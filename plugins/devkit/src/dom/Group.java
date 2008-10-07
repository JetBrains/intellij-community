// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * plugin.dtd:group interface.
 */
public interface Group extends DomElement {

	/**
	 * Returns the value of the popup child.
	 * Attribute popup
	 * @return the value of the popup child.
	 */
	@NotNull
	GenericAttributeValue<String> getPopup();


	/**
	 * Returns the value of the icon child.
	 * Attribute icon
	 * @return the value of the icon child.
	 */
	@NotNull
	GenericAttributeValue<String> getIcon();


	/**
	 * Returns the value of the description child.
	 * Attribute description
	 * @return the value of the description child.
	 */
	@NotNull
	GenericAttributeValue<String> getDescription();


	/**
	 * Returns the value of the class child.
	 * Attribute class
	 * @return the value of the class child.
	 */
	@NotNull
	@com.intellij.util.xml.Attribute ("class")
        @ExtendClass(value = "com.intellij.openapi.actionSystem.ActionGroup",
            instantiatable = true, allowAbstract = false, allowInterface = false)
	GenericAttributeValue<PsiClass> getClazz();


	/**
	 * Returns the value of the text child.
	 * Attribute text
	 * @return the value of the text child.
	 */
	@NotNull
	GenericAttributeValue<String> getText();


        @NotNull
        GenericAttributeValue<String> getId();

	/**
	 * Returns the list of reference children.
	 * @return the list of reference children.
	 */
	@NotNull
	List<Reference> getReferences();
	/**
	 * Adds new child to the list of reference children.
	 * @return created child
	 */
	Reference addReference();


	/**
	 * Returns the value of the separator child.
	 * @return the value of the separator child.
	 */
	@NotNull
	List<GenericDomValue<String>> getSeparators();


	/**
	 * Returns the list of action children.
	 * @return the list of action children.
	 */
	@NotNull
	List<Action> getActions();
	/**
	 * Adds new child to the list of action children.
	 * @return created child
	 */
	Action addAction();


	/**
	 * Returns the list of add-to-group children.
	 * @return the list of add-to-group children.
	 */
	@NotNull
	List<AddToGroup> getAddToGroups();
	/**
	 * Adds new child to the list of add-to-group children.
	 * @return created child
	 */
	AddToGroup addAddToGroup();


}
