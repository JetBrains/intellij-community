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
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;

import javax.swing.*;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 12:50 PM
 */
public class GradleTasksModel extends DefaultListModel {
  
  public static final Comparator<GradleTaskDescriptor> BY_NAME_COMPARATOR = new Comparator<GradleTaskDescriptor>() {
    @Override
    public int compare(GradleTaskDescriptor d1, GradleTaskDescriptor d2) {
      return d1.getName().compareTo(d2.getName());
    }
  };

  public void setTasks(@NotNull Collection<GradleTaskDescriptor> taskDescriptors) {
    clear();
    ArrayList<GradleTaskDescriptor> descriptorsToUse = ContainerUtilRt.newArrayList(taskDescriptors);
    Collections.sort(descriptorsToUse, BY_NAME_COMPARATOR);
    for (GradleTaskDescriptor descriptor : descriptorsToUse) {
      addElement(descriptor);
    }
  }

  public void setFirst(@NotNull GradleTaskDescriptor descriptor) {
    insertElementAt(descriptor, 0);
    for (int i = 1; i < size(); i++) {
      if (descriptor.equals(getElementAt(i))) {
        remove(i);
        return;
      }
    }

    if (size() > 1) {
      remove(size() - 1);
    }
  }

  @NotNull
  public List<GradleTaskDescriptor> getTasks() {
    List<GradleTaskDescriptor> result = ContainerUtilRt.newArrayList();
    for (int i = 0; i < size(); i++) {
      Object e = getElementAt(i);
      if (e instanceof GradleTaskDescriptor) {
        result.add((GradleTaskDescriptor)e);
      }
    }
    return result;
  }

  public void ensureSize(int elementsNumber) {
    int toAdd = elementsNumber - size();
    if (toAdd <= 0) {
      return;
    }
    while (--toAdd >= 0) {
      addElement(new MyEmptyDescriptor());
    }
  }

  static class MyEmptyDescriptor {
  }
}
