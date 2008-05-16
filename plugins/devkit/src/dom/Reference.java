// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:reference interface.
 */
public interface Reference extends DomElement {

	/**
	 * Returns the value of the id child.
	 * Attribute id
	 * @return the value of the id child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getRef();


}
