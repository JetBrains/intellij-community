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

import com.intellij.openapi.project.Project;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;

/**
 <!=========================================================================>
 This allows you to listen for the gradle UI coming and going.

 @author mhunsicker
<!==========================================================================>*/
public interface GradleUIAvailabilityObserver
{
   /**<!===== gradleUILoaded =================================================>
    Notification that the gradle UI has been loaded.

    <!      Name                 Description>
    @param  singlePaneUIVersion1 the main gradle UI object.
    @param ideaProject
    @author mhunsicker
    <!=======================================================================>*/
   public void gradleUILoaded( SinglePaneUIVersion1 singlePaneUIVersion1, Project ideaProject );

   /**<!===== gradleUIUnloaded ===============================================>
    Notification that the gradle UI has been unloaded.
    @author mhunsicker
    <!=======================================================================>*/
   public void gradleUIUnloaded();
}
