/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.internal.statistic.beans;

public class GroupDescriptor implements Comparable<GroupDescriptor> {
    public static final double DEFAULT_PRIORITY = 0.0;
    public static final double HIGHER_PRIORITY = 100.0;
    public static final double LOWER_PRIORITY = -100.0;

  public static final int MAX_ID_LENGTH = 30;

  private final String myId;
    private double myPriority;

    public static GroupDescriptor create(String id) {
      return new GroupDescriptor(id);
    }

    public static GroupDescriptor create(String id, double priority) {
        assert id != null;
        return new GroupDescriptor(id, priority);
    }

    private GroupDescriptor(String id) {
        this(id, DEFAULT_PRIORITY);
    }

    private GroupDescriptor(String id, double priority) {
        assert id != null;
        myId = ConvertUsagesUtil.ensureProperKey(id);
        myPriority = priority;
    }


    public String getId() {
        return myId;
    }

    public double getPriority() {
        return myPriority;
    }

    public void setPriority(double priority) {
        myPriority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupDescriptor)) return false;

        GroupDescriptor that = (GroupDescriptor) o;

        if (myId != null ? !myId.equals(that.myId) : that.myId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return myId != null ? myId.hashCode() : 0;
    }

    public int compareTo(GroupDescriptor gd) {
        final int priority = (int) (this.getPriority() - gd.getPriority());
        return priority == 0 ? gd.getId().compareTo(gd.getId()) : priority;
    }
}

