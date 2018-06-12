/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;

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
	GenericAttributeValue<Anchor> getAnchor();


	/**
	 * Returns the value of the relative-to-action child.
	 * Attribute relative-to-action
	 * @return the value of the relative-to-action child.
	 */
	@NotNull
	@Convert(ActionOrGroupResolveConverter.class)
        GenericAttributeValue<ActionOrGroup> getRelativeToAction();


	/**
	 * Returns the value of the group-id child.
	 * Attribute group-id
	 * @return the value of the group-id child.
	 */
	@NotNull
	@Required
	@Convert(ActionOrGroupResolveConverter.OnlyGroups.class)
	GenericAttributeValue<ActionOrGroup> getGroupId();


}
