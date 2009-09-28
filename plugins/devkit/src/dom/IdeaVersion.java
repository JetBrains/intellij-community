// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

public interface IdeaVersion extends DomElement {
	@NotNull
	GenericAttributeValue<String> getMax();

	@NotNull
	@Required
	GenericAttributeValue<String> getSinceBuild();

	@NotNull
	GenericAttributeValue<String> getUntilBuild();


	@NotNull
	GenericAttributeValue<String> getMin();
}
