// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:helpset interface.
 * Type helpset documentation
 * <pre>
 *  helpset is a name of file from PLUGIN/help/ folder
 *   Example: <helpset file="myhelp.jar" path="/Help.hs"/>
 *  
 * </pre>
 */
public interface Helpset extends DomElement {

	/**
	 * Returns the value of the file child.
	 * Attribute file
	 * @return the value of the file child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getFile();


	/**
	 * Returns the value of the path child.
	 * Attribute path
	 * @return the value of the path child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getPath();


}
