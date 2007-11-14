// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Actions extends DomElement {

	@NotNull
	List<Action> getActions();
	Action addAction();


	@NotNull
	List<Group> getGroups();
	Group addGroup();
}
