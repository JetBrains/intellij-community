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

package org.jetbrains.android.util;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 17, 2009
 * Time: 10:44:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class OrderRoot {
  private final OrderRootType myType;
  private final VirtualFile myFile;

  public OrderRoot(OrderRootType type, VirtualFile file) {
    myType = type;
    myFile = file;
  }

  public OrderRootType getType() {
    return myType;
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
