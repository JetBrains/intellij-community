// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * plugin.dtd:action interface.
 */
public interface Action extends DomElement {

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
	@Required
	GenericAttributeValue<PsiClass> getClazz();


	/**
	 * Returns the value of the text child.
	 * Attribute text
	 * @return the value of the text child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getText();


	/**
	 * Returns the value of the id child.
	 * Attribute id
	 * @return the value of the id child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getId();


	/**
	 * Returns the list of keyboard-shortcut children.
	 * @return the list of keyboard-shortcut children.
	 */
	@NotNull
	List<KeyboardShortcut> getKeyboardShortcuts();
	/**
	 * Adds new child to the list of keyboard-shortcut children.
	 * @return created child
	 */
	KeyboardShortcut addKeyboardShortcut();


	/**
	 * Returns the list of mouse-shortcut children.
	 * @return the list of mouse-shortcut children.
	 */
	@NotNull
	List<MouseShortcut> getMouseShortcuts();
	/**
	 * Adds new child to the list of mouse-shortcut children.
	 * @return created child
	 */
	MouseShortcut addMouseShortcut();


	/**
	 * Returns the list of shortcut children.
	 * @return the list of shortcut children.
	 */
	@NotNull
	List<Shortcut> getShortcuts();
	/**
	 * Adds new child to the list of shortcut children.
	 * @return created child
	 */
	Shortcut addShortcut();


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
