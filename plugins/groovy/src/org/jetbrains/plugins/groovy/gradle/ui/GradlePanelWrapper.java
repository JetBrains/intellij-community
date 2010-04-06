/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gradle.ui;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.openapi.external.ui.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gradle.GradleLibraryManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 <!=========================================================================>
 This shows a self-contained gradle UI inside a panel. It may also show a
 gradle setup panel if gradle hasn't been configured or was unable to load.

 The gradle UI is loaded dynamically from an external gradle installation.
 There is a gradle open API jar that does the real work (and is part of gradle).
 The gradle UI implementation uses versioned interfaces and wrappers to interact
 with us. This allows us to be forward and backward compatible with gradle
 (although certain major changes will break us). This allows the gradle UI to
 change and gain new features that we don't have to know about (new features that
 require our interaction obviously won't work, but the wrappers provide an
 appropriate default behavior that is defined inside gradle).

 You get the SinglePaneUIVersion1 here as well as add some custom panels to it.

 @author mhunsicker
 <!==========================================================================> */
public class GradlePanelWrapper
{
   private final JPanel mainPanel = new JPanel( new BorderLayout() );
   private Project myProject;
   private GradleUISettings settings;
   private SinglePaneUIVersion1 singlePaneUIVersion1;
   private GradleSetupPanel gradleSetupPanel;
   private final List<GradleTabVersion1> additionalTabs = new ArrayList<GradleTabVersion1>();
   private final ObserverLord<GradleUIAvailabilityObserver> observerLord = new ObserverLord<GradleUIAvailabilityObserver>();

   public void initalize(@Nullable Module module, Project myProject)
   {
      this.myProject = myProject;
      this.settings = GradleUISettings.getInstance(myProject);

      File gradleHomeDirectory = new File( GradleLibraryManager.getSdkHome( module, myProject ).getPath() );

      //this will either load the UI from gradle or display the gradle setup panel.
      setupUI( gradleHomeDirectory );
   }

   public Project getProject() { return myProject; }

   private boolean setupUI( final File gradleHomeDirectory )
   {
      try
      {
         if( gradleHomeDirectory == null )
         {
            addGradleSetupPanel( "Gradle not configured", (String) null );
            return false;
         }

         if( !gradleHomeDirectory.exists()  )
         {
            addGradleSetupPanel( "Gradle directory does not exist.", gradleHomeDirectory.getAbsolutePath() );
            return false;
         }

         //Since this is Swing-related we want to make sure its always executed in the EDT.
         //When called by Idea, we're not in the EDT. When called from out setup panel, we
         //ARE in the EDT. If this fails, it will add the gradle setup panel.
         if( SwingUtilities.isEventDispatchThread() )
            loadUIFromGradle( gradleHomeDirectory );
         else
            SwingUtilities.invokeAndWait( new Runnable()
            {
               public void run()
               {
                  loadUIFromGradle( gradleHomeDirectory );
               }
            } );

         if( singlePaneUIVersion1 != null )
         {
            //by default, we'll set it to your project's directory, but this will probably be overridden when its settings are loaded in aboutToShow.
            singlePaneUIVersion1.setCurrentDirectory( new File( myProject.getBaseDir().getPath() ) );
            addAdditionalTabs();
            setMainPanelContents( singlePaneUIVersion1.getComponent() );
            singlePaneUIVersion1.aboutToShow();
            return true;
         }
      }
      catch( Exception e )
      {
         singlePaneUIVersion1 = null;
         notifyGradleUIUnloaded();
         addGradleSetupPanel( "Failed to load the gradle library.", e );
      }

      return false;
   }

   /**<!===== loadUIFromGradle ===============================================>
      This dynamically loads the UI from a gradle installation. We call into
      a function inside the gradle open API jar that handles all the reflection
      of loading the classes. If this fails, we show the gradle setup panel.

      <!      Name                Description>
      @param  gradleHomeDirectory where gradle resides
      @author mhunsicker
   <!=======================================================================>*/
   private void loadUIFromGradle( File gradleHomeDirectory )
   {
      try
      {
         singlePaneUIVersion1 = UIFactory.createUI( GradlePanelWrapper.class.getClassLoader(), gradleHomeDirectory, new IdeaUIInteraction(), false );
         final SinglePaneUIVersion1 singlePaneUIVersion1 = getSinglePaneUIVersion1();
      final Project project = getProject();

      observerLord.notifyObservers( new ObserverLord.ObserverNotification<GradleUIAvailabilityObserver>()
      {
         public void notify( GradleUIAvailabilityObserver observer )
         {
            observer.gradleUILoaded( singlePaneUIVersion1, project );
         }
      } );
      }
      catch( Throwable e )
      {
         singlePaneUIVersion1 = null;
         notifyGradleUIUnloaded();
         e.printStackTrace();
         addGradleSetupPanel( "Failed to load the gradle library.", e );
      }
   }

  /*package*/ void notifyGradleUIUnloaded()
  {
     observerLord.notifyObservers( new ObserverLord.ObserverNotification<GradleUIAvailabilityObserver>()
     {
        public void notify( GradleUIAvailabilityObserver observer )
        {
           observer.gradleUIUnloaded();
        }
     } );
  }


   public JPanel getComponent()
   {
      return mainPanel;
   }

   /**<!===== addGradleSetupPanel ============================================>
      This sets the main panel to the gradle setup panel. This is used when
      we could not load gradle either because of an error or lack of setup.

      <!      Name       Description>
      @param  message    a message of why we're displaying this.
      @param  messageDetails detailed information about why we're not displaying this.
                         this will only be visible if the user chooses to show it.
      @author mhunsicker
   <!=======================================================================>*/
   private void addGradleSetupPanel( String message, String messageDetails )
   {
      if( gradleSetupPanel == null )
         gradleSetupPanel = new GradleSetupPanel();

      gradleSetupPanel.setMessage( message, messageDetails );
      setMainPanelContents( gradleSetupPanel.getComponent() );
   }

   private void addGradleSetupPanel( String message, Throwable throwable )
   {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      throwable.printStackTrace(pw);

      addGradleSetupPanel( message, throwable.getMessage() + "\n" + sw.toString() );
   }

   /**<!===== setMainPanelContents ===========================================>
      This replaces the main panel's contents with the specified new component.

      @author mhunsicker
   <!=======================================================================>*/
   private void setMainPanelContents( Component newComponent )
   {
      mainPanel.removeAll();
      mainPanel.invalidate();
      mainPanel.add( newComponent, BorderLayout.CENTER );
      mainPanel.validate();
      mainPanel.repaint();
   }

   //
         /**<!=========================================================================>
            This class is how we interact with Gradle, specifically how to specify
            what we use to interact with it. The purpose of SinglePaneUIInteractionVersion1
            is to aid forward/backward compatibility.
            @author mhunsicker
         <!==========================================================================>*/
         private class IdeaUIInteraction implements SinglePaneUIInteractionVersion1
         {
            private IdeaUIInteraction()
            {
            }

            /**<!===== instantiateAlternateUIInteraction ==============================>
               This is only called once and is how we get ahold of the AlternateUIInteraction.
               @return an AlternateUIInteraction object. This cannot be null.
               @author mhunsicker
            <!=======================================================================>*/
            public AlternateUIInteractionVersion1 instantiateAlternateUIInteraction()
            {
               return new IdeaAlternateUIInteraction();
            }

            /**<!===== instantiateSettings ============================================>
             This is only called once and is how we get ahold of how the owner wants
             to store preferences.
             @return a settings object. This cannot be null.
             @author mhunsicker
             <!=======================================================================>*/
            public SettingsNodeVersion1 instantiateSettings()
            {
               return settings.getRootNode();
            }
         }

   //
         /**<!=========================================================================>
            @author mhunsicker
         <!==========================================================================>*/
         private class IdeaAlternateUIInteraction implements AlternateUIInteractionVersion1
         {
             /**<!===== editFiles ======================================================>
               This is called when we should edit the specified files. Open them in the
               current IDE or some external editor.

               <!      Name       Description>
               @param  files      the files to open
               @author mhunsicker
            <!=======================================================================>*/
            public void editFiles( List<File> files )
            {
                Iterator<File> iterator = files.iterator();
               while( iterator.hasNext() )
               {
                  File file = iterator.next();
                  editFile( file );
               }
            }

            /**<!===== doesSupportEditingFiles ========================================>
               Determines if we can call editFiles. This is not a dynamic answer and
               should always return either true of false. If you want to change the
               answer, return true and then handle the files differently in editFiles.
               @return true if support editing files, false otherwise.
               @author mhunsicker
            <!=======================================================================>*/
            public boolean doesSupportEditingFiles()
            {
               return true;
            }
         }

    /**<!===== editFile =======================================================>
      Opens a single file in Idea.

      <!      Name       Description>
      @param  file       the file to open
      @author mhunsicker
   <!=======================================================================>*/
   private void editFile( File file )
   {
      if( file != null && file.exists() )
      {
         VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile( file );
         if( virtualFile != null )
            FileEditorManager.getInstance( myProject ).openFile( virtualFile, true );
      }
   }

   public void close()
   {
      if( singlePaneUIVersion1 != null )
      {
         singlePaneUIVersion1.close();

         //I'm going to clear this out because I think this is being called multiple times.
         setMainPanelContents( new JLabel( "Closing" ) );
         singlePaneUIVersion1 = null;
         notifyGradleUIUnloaded();
      }
   }

   /**<!===== canClose =======================================================>
      Call this to determine if we can close. We'll just ask the .
      @return .
      @author mhunsicker
   <!=======================================================================>*/
   public boolean canClose()
   {
      if( singlePaneUIVersion1 != null )
         return singlePaneUIVersion1.canClose( new SinglePaneUIVersion1.CloseInteraction()
         {
            public boolean promptUserToConfirmClosingWhileBusy()
            {
               int result = JOptionPane.showConfirmDialog( SwingUtilities.getWindowAncestor( mainPanel ), "Gradle tasks are being currently being executed. Exit anyway?", "Exit While Busy?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
               return result == JOptionPane.YES_OPTION;
            }
         } );

      return true;
   }

   /**<!===== addTab =========================================================>
      This adds a tab. You'll want to add tabs using this rather than calling
      SinglePaneUIVersion1.addTab directly. Why? Because this removes the timing
      issues. That is, whether or not we've loaded the gradle UI, you can call
      this and we'll show the tabs whenever we do display the UI.
      <!      Name       Description>
      @param  tab        the tab to add
      @author mhunsicker
   <!=======================================================================>*/
   public void addTab( GradleTabVersion1 tab )
   {
      additionalTabs.add( tab );
      if( singlePaneUIVersion1 != null )
         singlePaneUIVersion1.addTab( singlePaneUIVersion1.getGradleTabCount() + 1, tab );
   }

   public void removeTab( GradleTabVersion1 tab )
   {
      additionalTabs.remove( tab );
      if( singlePaneUIVersion1 != null )
         singlePaneUIVersion1.removeTab( tab );
   }

   private void addAdditionalTabs()
   {
      Iterator<GradleTabVersion1> iterator = additionalTabs.iterator();
      while( iterator.hasNext() )
      {
         GradleTabVersion1 gradleTab = iterator.next();
         singlePaneUIVersion1.addTab( singlePaneUIVersion1.getGradleTabCount() + 1, gradleTab );
      }
   }

   /**<!===== getSinglePaneUIVersion1 ========================================>
      @return the single pane UI version1. Note: this is very likely to return
              null if this hasn't been setup yet or hasn't loaded yet.
      @author mhunsicker
   <!=======================================================================>*/
   public SinglePaneUIVersion1 getSinglePaneUIVersion1()
   {
      return singlePaneUIVersion1;
   }
}
