// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:mouse-shortcut interface.
 */
public interface MouseShortcut extends DomElement {

	/**
	 * Returns the value of the keymap child.
	 * Attribute keymap
	 * @return the value of the keymap child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getKeymap();


	/**
	 * Returns the value of the keystroke child.
	 * Attribute keystroke
	 * @return the value of the keystroke child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getKeystroke();


}
