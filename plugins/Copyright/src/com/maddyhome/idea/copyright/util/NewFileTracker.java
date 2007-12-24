package com.maddyhome.idea.copyright.util;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

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

    private Set<VirtualFile> newfiles = new HashSet<VirtualFile>();

    private static NewFileTracker instance = new NewFileTracker();
}