// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.IdeaPluginConverter;

@Convert(IdeaPluginConverter.class)
public interface Dependency extends GenericDomValue<IdeaPlugin> {
	@NotNull
	GenericAttributeValue<String> getOptional();
	@NotNull
	GenericAttributeValue<String> getConfigFile();
}
