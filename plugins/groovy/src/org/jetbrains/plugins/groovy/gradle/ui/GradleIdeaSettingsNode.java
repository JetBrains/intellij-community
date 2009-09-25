/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.gradle.ui;

import org.gradle.openapi.external.ui.SettingsNodeVersion1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**<!=========================================================================>
   This is our Idea-side implementation of a settings node. We'll store them in
   a hashmap and then write them out to the Idea project file using its
   serialization mechanism. The settings are actually a hiearchy of nodes
   similar to xml, but without attributes.
   @author mhunsicker
<!==========================================================================>*/
public class GradleIdeaSettingsNode implements SettingsNodeVersion1
{
   public List<SettingsNodeVersion1> children = new ArrayList<SettingsNodeVersion1>();
   public String name;
   public String value;
   public GradleIdeaSettingsNode parent;

   public GradleIdeaSettingsNode( String name, GradleIdeaSettingsNode parent )
   {
      this.name = name;
      this.parent = parent;
   }

   public void setName( String name )
   {
      this.name = name;
   }

   public String getName()
   {
      return name;
   }

   public void setValue( String value )
   {
      this.value = value;
   }

   public String getValue()
   {
      return value;
   }

   public List<SettingsNodeVersion1> getChildNodes()
   {
      return Collections.unmodifiableList( children );
   }

   public List<SettingsNodeVersion1> getChildNodes( String name )
   {
      List<SettingsNodeVersion1> matchingChildren = new ArrayList<SettingsNodeVersion1>( );

      Iterator<SettingsNodeVersion1> iterator = children.iterator();

      while( iterator.hasNext() )
      {
         SettingsNodeVersion1 settingsNodeVersion1 = iterator.next();
         if( name.equals( settingsNodeVersion1.getName() ) )
            matchingChildren.add( settingsNodeVersion1 );
      }

      return matchingChildren;
   }

   public SettingsNodeVersion1 getChildNode( String name )
   {
      Iterator<SettingsNodeVersion1> iterator = children.iterator();

      while( iterator.hasNext() )
      {
         SettingsNodeVersion1 settingsNodeVersion1 = iterator.next();
         if( name.equals( settingsNodeVersion1.getName() ) )
            return settingsNodeVersion1;
      }

      return null;
   }

   public SettingsNodeVersion1 addChild( String name )
   {
      GradleIdeaSettingsNode node = new GradleIdeaSettingsNode( name, this );
      children.add( node );
      return node;
   }

   public SettingsNodeVersion1 addChildIfNotPresent( String name )
   {
      SettingsNodeVersion1 child = getChildNode( name );
      if( child == null )
         child = addChild( name );

      return child;
   }


   public SettingsNodeVersion1 getNodeAtPath( String ... pathPortions )
   {
      if( pathPortions == null || pathPortions.length == 0 )
         return null;

      String firstPathPortion = pathPortions[ 0 ];

      SettingsNodeVersion1 currentNode = getChildNode( firstPathPortion );

      int index = 1;//Skip the first one. we've already used that one.
      while( index < pathPortions.length && currentNode != null )
      {
         String pathPortion = pathPortions[ index ];
         currentNode = currentNode.getChildNode( pathPortion );
         index++;
      }

      return currentNode;
   }

   private SettingsNodeVersion1 getNodeAtPathCreateIfNotFound( String ... pathPortions )
   {
      if( pathPortions == null || pathPortions.length == 0 )
         return null;

      String firstPathPortion = pathPortions[ 0 ];

      SettingsNodeVersion1 currentNode = getChildNode( firstPathPortion );
      if( currentNode == null )
         currentNode = addChild( firstPathPortion );

      int index = 1;
      while( index < pathPortions.length )
      {
         String pathPortion = pathPortions[ index ];
         currentNode = currentNode.getChildNode( pathPortion );
         if( currentNode == null )
            currentNode = addChild( firstPathPortion );

         index++;
      }

      return currentNode;
   }

   public void removeAllChildren()
   {
      children.clear();
   }

   public void removeFromParent()
   {
      if( parent != null )
      {
         if( parent.removeChild( this ) )
            parent = null;
      }
   }

   private boolean removeChild( GradleIdeaSettingsNode node )
   {
      return children.remove( node );
   }

   public void setValueOfChild( String name, String value )
   {
      SettingsNodeVersion1 settingsNode = addChildIfNotPresent( name );
      settingsNode.setValue( value );
   }

   public String getValueOfChild( String name, String defaultValue )
   {
      SettingsNodeVersion1 settingsNode = getChildNode( name );
      if( settingsNode == null )
         return defaultValue;

      return settingsNode.getValue();
   }

   public int getValueOfChildAsInt( String name, int defaultValue )
   {
      SettingsNodeVersion1 settingsNode = getChildNode( name );
      if( settingsNode != null )
      {
         String value = settingsNode.getValue();

         try
         {
            if( value != null )
               return Integer.parseInt( value );
         }
         catch( NumberFormatException e )
         {
            //we couldn't parse it. Just return the default.
         }
      }
      return defaultValue;
   }

   public void setValueOfChildAsInt( String name, int value )
   {
      setValueOfChild( name, Integer.toString( value ) );
   }

   public long getValueOfChildAsLong( String name, long defaultValue )
   {
      SettingsNodeVersion1 settingsNode = getChildNode( name );
      if( settingsNode != null )
      {
         String value = settingsNode.getValue();

         try
         {
            if( value != null )
               return Long.parseLong( value );
         }
         catch( NumberFormatException e )
         {
            //we couldn't parse it. Just return the default.
         }
      }
      return defaultValue;
   }

   public void setValueOfChildAsLong( String name, long value )
   {
      setValueOfChild( name, Long.toString( value ) );
   }

   public boolean getValueOfChildAsBoolean( String name, boolean defaultValue )
   {
      SettingsNodeVersion1 settingsNode = getChildNode( name );
      if( settingsNode != null )
      {
         String value = settingsNode.getValue();

	      //I'm not calling 'Boolean.parseBoolean( value )' because it will return false if the value isn't true/false
         //and we want it to return whatever the default is if its not a boolean. This is how the Int and Long functions
         //behave.
         if( value != null )
         {
            if( "true".equalsIgnoreCase( value ) )
               return true;

            if( "false".equalsIgnoreCase( value ) )
               return false;
         }
      }

      return defaultValue;
   }

   public void setValueOfChildAsBoolean( String name, boolean value )
   {
      setValueOfChild( name, Boolean.toString( value ) );
   }

   @Override
   public String toString()
   {
      return getName() + "='" + getValue() + "' " + children.size() + " children";
   }

   //this should only be called by the serializer
   public void setChildren( List<GradleIdeaSettingsNode> newChildren )
   {
      children.clear();
      children.addAll( newChildren );
   }
}
