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

package com.maddyhome.idea.copyright.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.HashSet;
import java.util.Set;

public class NewFileTracker
{
    public static NewFileTracker getInstance()
    {
        return instance;
    }

    public boolean contains(VirtualFile file)
    {
        synchronized (newfiles)
        {
            return newfiles.contains(file);
        }
    }

    public void remove(VirtualFile file)
    {
        synchronized (newfiles)
        {
            newfiles.remove(file);
        }
    }

    private NewFileTracker()
    {
        VirtualFileManager manager = VirtualFileManager.getInstance();
        manager.addVirtualFileListener(new VirtualFileAdapter()
        {
            public void fileCreated(VirtualFileEvent event)
            {
                synchronized (newfiles)
                {
                    newfiles.add(event.getFile());
                }
            }
        });
    }

    private final Set<VirtualFile> newfiles = new HashSet<VirtualFile>();

    private static final NewFileTracker instance = new NewFileTracker();
}