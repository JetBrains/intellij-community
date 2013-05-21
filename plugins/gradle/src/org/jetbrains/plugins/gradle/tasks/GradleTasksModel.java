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
package org.jetbrains.plugins.gradle.tasks;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 12:50 PM
 */
// TODO den remove
public class GradleTasksModel extends DefaultListModel {
  
//  public static final Comparator<ExternalSystemTaskDescriptor> BY_NAME_COMPARATOR = new Comparator<ExternalSystemTaskDescriptor>() {
//    @Override
//    public int compare(ExternalSystemTaskDescriptor d1, ExternalSystemTaskDescriptor d2) {
//      return d1.getName().compareTo(d2.getName());
//    }
//  };
//
//  public void setTasks(@NotNull Collection<ExternalSystemTaskDescriptor> taskDescriptors) {
//    clear();
//    ArrayList<ExternalSystemTaskDescriptor> descriptorsToUse = ContainerUtilRt.newArrayList(taskDescriptors);
//    Collections.sort(descriptorsToUse, BY_NAME_COMPARATOR);
//    for (ExternalSystemTaskDescriptor descriptor : descriptorsToUse) {
//      addElement(descriptor);
//    }
//  }
//
//  public void setFirst(@NotNull ExternalSystemTaskDescriptor descriptor) {
//    insertElementAt(descriptor, 0);
//    for (int i = 1; i < size(); i++) {
//      if (descriptor.equals(getElementAt(i))) {
//        remove(i);
//        return;
//      }
//    }
//
//    if (size() > 1) {
//      remove(size() - 1);
//    }
//  }
//
//  @NotNull
//  public List<ExternalSystemTaskDescriptor> getTasks() {
//    List<ExternalSystemTaskDescriptor> result = ContainerUtilRt.newArrayList();
//    for (int i = 0; i < size(); i++) {
//      Object e = getElementAt(i);
//      if (e instanceof ExternalSystemTaskDescriptor) {
//        result.add((ExternalSystemTaskDescriptor)e);
//      }
//    }
//    return result;
//  }
//
//  public void ensureSize(int elementsNumber) {
//    int toAdd = elementsNumber - size();
//    if (toAdd <= 0) {
//      return;
//    }
//    while (--toAdd >= 0) {
//      addElement(new MyEmptyDescriptor());
//    }
//  }
//
//  static class MyEmptyDescriptor {
//  }
}
