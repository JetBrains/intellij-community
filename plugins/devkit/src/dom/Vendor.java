/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:vendor interface.
 * Type vendor documentation
 * <pre>
 *     <vendor> tag now could have 'url', 'email' and 'logo' attributes;
 *     'logo' should contain path to a 16 x 16 icon that will appear near the plugin name in the IDEA Welcome Screen 
 * </pre>
 */
public interface Vendor extends DomElement {

	/**
	 * Returns the value of the simple content.
	 * @return the value of the simple content.
	 */
	@NotNull
        @NoSpellchecking
	String getValue();
	/**
	 * Sets the value of the simple content.
	 * @param value the new value to set
	 */
	void setValue(String value);


	/**
	 * Returns the value of the email child.
	 * Attribute email
	 * @return the value of the email child.
	 */
	@NotNull
        @NoSpellchecking
	GenericAttributeValue<String> getEmail();


	/**
	 * Returns the value of the url child.
	 * Attribute url
	 * @return the value of the url child.
	 */
	@NotNull
        @NoSpellchecking
	GenericAttributeValue<String> getUrl();


	/**
	 * Returns the value of the logo child.
	 * Attribute logo
	 * @return the value of the logo child.
	 */
	@NotNull
        @NoSpellchecking
	GenericAttributeValue<String> getLogo();


}
