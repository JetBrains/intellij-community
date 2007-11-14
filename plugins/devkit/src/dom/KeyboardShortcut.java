// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:keyboard-shortcut interface.
 */
public interface KeyboardShortcut extends DomElement {

	/**
	 * Returns the value of the first-keystroke child.
	 * Attribute first-keystroke
	 * @return the value of the first-keystroke child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getFirstKeystroke();


	/**
	 * Returns the value of the keymap child.
	 * Attribute keymap
	 * @return the value of the keymap child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getKeymap();


	/**
	 * Returns the value of the use-shortcut-of child.
	 * Attribute use-shortcut-of
	 * @return the value of the use-shortcut-of child.
	 */
	@NotNull
	GenericAttributeValue<String> getUseShortcutOf();


	/**
	 * Returns the value of the second-keystroke child.
	 * Attribute second-keystroke
	 * @return the value of the second-keystroke child.
	 */
	@NotNull
	GenericAttributeValue<String> getSecondKeystroke();


}
