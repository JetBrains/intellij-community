// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:add-to-group interface.
 */
public interface AddToGroup extends DomElement {

	/**
	 * Returns the value of the anchor child.
	 * Attribute anchor
	 * @return the value of the anchor child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getAnchor();


	/**
	 * Returns the value of the relative-to-action child.
	 * Attribute relative-to-action
	 * @return the value of the relative-to-action child.
	 */
	@NotNull
	GenericAttributeValue<String> getRelativeToAction();


	/**
	 * Returns the value of the group-id child.
	 * Attribute group-id
	 * @return the value of the group-id child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getGroupId();


}
