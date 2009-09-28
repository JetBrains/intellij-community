/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.gradle.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**<!=========================================================================>
   This is our Idea-side implementation of settings. We'll store them in
   a hashmap and then write them out to the Idea project file using its
   serialization mechanism.

   The actual settings are a hiearchy and are stored in instances of
   GradleIdeaSettingsNode. This class basically holds onto the root node and
   handles the serialization to/from Idea's project files.
   
   @author mhunsicker
<!==========================================================================>*/
@State(
    name = "GradleUISettings",
    storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/gradle_config.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleUISettings implements PersistentStateComponent<Element>
{
   private GradleIdeaSettingsNode rootNode = new GradleIdeaSettingsNode( "root", null );
   private static final String SETTING = "setting";
   private static final String NAME = "name";
   private static final String VALUE = "value";

   public GradleIdeaSettingsNode getRootNode() { return rootNode; }

   public Element getState()
   {
      Element e = new Element( SETTING );
      writeOutSetting(e, rootNode);
      return e;
   }

   public void loadState(Element element)
   {
      List<GradleIdeaSettingsNode> settingsNodes = readInSetting( element, null );
      if( !settingsNodes.isEmpty() )   //this should have either 1 or none in it.
         rootNode = settingsNodes.get( 0 );
   }

   public static GradleUISettings getInstance(final Project project)
   {
     return ServiceManager.getService(project, GradleUISettings.class);
   }

   private void writeOutSetting( Element parentElement, GradleIdeaSettingsNode node )
   {
      Element element = new Element( SETTING );
      parentElement.addContent( element );

      element.setAttribute( NAME, node.getName() );
      if( node.getValue() != null )
         element.setAttribute( VALUE, node.getValue() );

      for ( SettingsNodeVersion1 settingsNode : node.getChildNodes())
         writeOutSetting( element, (GradleIdeaSettingsNode)settingsNode );
   }

   /**<!===== readInSetting ==================================================>
      This does the real work of reading in settings. This recursively reads
      them and because both the parent and children know about each other, we
      have to track the children as we read them in.

      @author mhunsicker
   <!=======================================================================>*/
   private List<GradleIdeaSettingsNode> readInSetting( Element parentElement, GradleIdeaSettingsNode parentNode )
   {
      List<GradleIdeaSettingsNode> nodesReadIn = new ArrayList<GradleIdeaSettingsNode>();

      Iterator iterator = parentElement.getChildren( SETTING ).iterator();
      while( iterator.hasNext() )
      {
         Element element = (Element) iterator.next();

         String name = element.getAttributeValue( NAME );

         GradleIdeaSettingsNode node = new GradleIdeaSettingsNode( name, parentNode );

         String value = element.getAttributeValue( VALUE );
         if( value != null )
            node.setValue( value );

         //read in our children
         List<GradleIdeaSettingsNode> myChildren = readInSetting( element, node );

         //and store them
         node.setChildren( myChildren );

         nodesReadIn.add( node );
      }

      return nodesReadIn;
   }
}
