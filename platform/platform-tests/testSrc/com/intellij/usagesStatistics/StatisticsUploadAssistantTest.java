/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.LinkedHashMap;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatisticsUploadAssistantTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testConvertUsagesWithPriority() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> patchedUsages = new HashMap<>();

        createPatchDescriptor(patchedUsages, "low", GroupDescriptor.LOWER_PRIORITY, "l1", 1);
        createPatchDescriptor(patchedUsages, "low", GroupDescriptor.LOWER_PRIORITY, "l2", 1);
        createPatchDescriptor(patchedUsages, "high", GroupDescriptor.HIGHER_PRIORITY, "h", 1);
        createPatchDescriptor(patchedUsages, "high", GroupDescriptor.HIGHER_PRIORITY, "h2", 1);
        createPatchDescriptor(patchedUsages, "default_1", GroupDescriptor.DEFAULT_PRIORITY, "d11", 1);
        createPatchDescriptor(patchedUsages, "default_2", GroupDescriptor.DEFAULT_PRIORITY, "d21", 1);
        createPatchDescriptor(patchedUsages, "default_1", GroupDescriptor.DEFAULT_PRIORITY, "d12", 1);


        assertEquals(ConvertUsagesUtil.convertUsages(patchedUsages),
                     "high:h=1,h2=1;default_1:d11=1,d12=1;default_2:d21=1;low:l1=1,l2=1;");
    }

    public void testConvertUsagesWithEqualPriority() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> patchedUsages = new HashMap<>();

  createPatchDescriptor(patchedUsages, "g4", GroupDescriptor.HIGHER_PRIORITY, "1", 1);
        createPatchDescriptor(patchedUsages, "g2", GroupDescriptor.HIGHER_PRIORITY, "2", 1);
        createPatchDescriptor(patchedUsages, "g1", GroupDescriptor.HIGHER_PRIORITY, "3", 1);
        createPatchDescriptor(patchedUsages, "g3", GroupDescriptor.HIGHER_PRIORITY, "4", 1);


        assertEquals(ConvertUsagesUtil.convertUsages(patchedUsages), "g1:3=1;g2:2=1;g3:4=1;g4:1=1;");
    }

    public void testConvertString() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> patchedUsages = new HashMap<>();

        createPatchDescriptor(patchedUsages, "g4", GroupDescriptor.HIGHER_PRIORITY, "1", 1);
        createPatchDescriptor(patchedUsages, "g2", GroupDescriptor.HIGHER_PRIORITY, "2", 1);
        createPatchDescriptor(patchedUsages, "g1", GroupDescriptor.HIGHER_PRIORITY, "3", 1);
        createPatchDescriptor(patchedUsages, "g3", GroupDescriptor.HIGHER_PRIORITY, "4", 1);

        assertMapEquals(patchedUsages, ConvertUsagesUtil.convertString("g3:4=1;g1:3=1;g4:1=1;g2:2=1;"));
    }

    public void testConvertEmptyString() {
        assertEquals(ConvertUsagesUtil.convertString("").size(), 0);
    }

    public void testConvertBrokenString() {
        assertEquals(ConvertUsagesUtil.convertString("asdfasdfsad").size(), 0);
        assertEquals(ConvertUsagesUtil.convertString("asdf:asdfsad").size(), 0);
        assertEquals(ConvertUsagesUtil.convertString("asdfa:sd;fsad").size(), 0);
        assertEquals(ConvertUsagesUtil.convertString("asdfa:sd;fs,ad").size(), 0);
        assertEquals(ConvertUsagesUtil.convertString("asdfa:sd;f;sad").size(), 0);
        assertEquals(ConvertUsagesUtil.convertString("asdfa:sd=ds2,f=f,sad=;").size(), 0);
    }

    private static <T extends UsageDescriptor> void  assertMapEquals(@NotNull Map<GroupDescriptor, Set<T>> expected, @NotNull Map<GroupDescriptor, Set<UsageDescriptor>> actual) {
        assertEquals(expected.size(), actual.size());

        for (Map.Entry<GroupDescriptor, Set<T>> expectedEntry : expected.entrySet()) {
            final GroupDescriptor expectedGroupDescriptor = expectedEntry.getKey();

            assertTrue(actual.containsKey(expectedGroupDescriptor));

            assertSetEquals(expectedEntry.getValue(), actual.get(expectedEntry.getKey()));
        }
    }

    private static void assertSetEquals(@NotNull Set<? extends UsageDescriptor> expected, @NotNull Set<? extends UsageDescriptor> actual) {
        assertEquals(expected.size(), actual.size());
        for (UsageDescriptor usageDescriptor : expected) {
            boolean exists = false;
            for (UsageDescriptor descriptor : actual) {
                if (usageDescriptor.getKey().equals(descriptor.getKey()) && usageDescriptor.getValue() == descriptor.getValue()) {
                    exists = true;
                    break;
                }
            }
            assertTrue(asString(usageDescriptor) + " usage doesn't exist", exists);
        }
    }

    private static String asString(UsageDescriptor usage) {
        return usage.getKey() + "=" + usage.getValue();
    }

    private static void createPatchDescriptor(Map<GroupDescriptor, Set<UsageDescriptor>> patchedUsages, String groupId, double priority, String key, int i) {
        final GroupDescriptor groupDescriptor = GroupDescriptor.create(groupId, priority);

        if (!patchedUsages.containsKey(groupDescriptor)){
            patchedUsages.put(groupDescriptor, new LinkedHashSet<>());
        }
        patchedUsages.get(groupDescriptor).add(new UsageDescriptor(key, i));
    }


    protected static UsageDescriptor createDescriptor(String k, int i) {
        return new UsageDescriptor(k, i);
    }

    protected static Map<GroupDescriptor, Set<UsageDescriptor>> createDescriptors(String... strs) {
        Map<GroupDescriptor, Set<UsageDescriptor>> set = new LinkedHashMap<>();
        for (String str : strs) {
            final List<String> list = StringUtil.split(str, ":");
            final GroupDescriptor g = GroupDescriptor.create(list.get(0));
            if (!set.containsKey(g)) {
                set.put(g, new LinkedHashSet<>());
            }
            set.get(g).add(createDescriptor(list.get(1), Integer.parseInt(list.get(2))));
        }

        return set;
    }
}
