package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.PatchedUsage;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.persistence.BasicSentUsagesPersistenceComponent;
import com.intellij.internal.statistic.persistence.SentUsagesPersistence;
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

    public void testCreateNewPatch() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> all = createDescriptors("g:a1:1", "g:a2:2", "g:a3:3");
        final Map<GroupDescriptor, Set<UsageDescriptor>> sent = new HashMap<GroupDescriptor, Set<UsageDescriptor>>();

        final Map<GroupDescriptor, Set<PatchedUsage>> patched = StatisticsUploadAssistant.getPatchedUsages(all, sent);

        assertMapEquals(patched, createDescriptors("g:a1:1", "g:a2:2", "g:a3:3"));
    }

    public void testEmptyPatchs() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> all = new HashMap<GroupDescriptor, Set<UsageDescriptor>>();
        final Map<GroupDescriptor, Set<UsageDescriptor>> sent = new HashMap<GroupDescriptor, Set<UsageDescriptor>>();
        assertEquals(StatisticsUploadAssistant.getPatchedUsages(all, sent).size(), 0);
    }

    public void testCreateEmptyPatch() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> all = createDescriptors("g:a1:1", "g:a2:2", "g:a3:3", "g2:a1:1", "g2:a2:2", "g2:a3:3");
        final Map<GroupDescriptor, Set<UsageDescriptor>> sent = createDescriptors("g:a1:1", "g:a2:2", "g:a3:3", "g2:a1:1", "g2:a2:2", "g2:a3:3");

        assertEquals(StatisticsUploadAssistant.getPatchedUsages(all, sent).size(), 0);
    }

    public void testCreatePatchEmptyAll() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> all = new HashMap<GroupDescriptor, Set<UsageDescriptor>>();
        final Map<GroupDescriptor, Set<UsageDescriptor>> sent = createDescriptors("g:a1:1", "g:a2:2", "g2:a1:1", "g2:a2:2", "g2:a3:3");

        final Map<GroupDescriptor, Set<PatchedUsage>> patched = StatisticsUploadAssistant.getPatchedUsages(all, sent);

        assertMapEquals(patched, createDescriptors("g:a1:-1", "g2:a2:-2", "g2:a3:-3", "g:a2:-2", "g2:a1:-1"));
    }

    public void testCreatePatchMerged() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> all = createDescriptors("g:a1:100", "g:a2:2", "g2:a1:0", "g2:a2:1");
        final Map<GroupDescriptor, Set<UsageDescriptor>> sent = createDescriptors("g:a1:2", "g:a2:100", "g2:a1:1", "g2:a2:1", "g2:a3:3");

        final Map<GroupDescriptor, Set<PatchedUsage>> patched = StatisticsUploadAssistant.getPatchedUsages(all, sent);

        assertMapEquals(patched, createDescriptors("g:a1:98", "g:a2:-98", "g2:a1:-1", "g2:a3:-3"));
    }

    public void testPersistSentPatch() {
        final Map<GroupDescriptor, Set<UsageDescriptor>> allUsages = createDescriptors("g:a1:-1", "g2:a2:-2", "g2:a3:-3", "g:a2:-2", "g2:a1:-1", "g3:a1:13");
        final SentUsagesPersistence usagesPersistence = new BasicSentUsagesPersistenceComponent();

        Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        String result = StatisticsUploadAssistant.getStringPatch(patchedUsages, 500);
        StatisticsUploadAssistant.persistSentPatch(result, usagesPersistence);

        assertMapEquals(ConvertUsagesUtil.convertString(result), ConvertUsagesUtil.convertString("g:a2=-2,a1=-1;g2:a3=-3,a2=-2,a1=-1;g3:a1=13;"));

        patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        result = StatisticsUploadAssistant.getStringPatch(patchedUsages, 500);
        StatisticsUploadAssistant.persistSentPatch(result, usagesPersistence);

        assertEquals("sent usages must be persisted", result.length(), 0);
        assertEquals(allUsages.size(), usagesPersistence.getSentUsages().size());
    }

    public void testConvertUsages() {
        final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = StatisticsUploadAssistant
                .getPatchedUsages(createDescriptors("g:a1:-1", "g2:a2:-2", "g2:a3:-3", "g:a2:-2", "g2:a1:-1", "g3:a1:13"),
                                  new HashMap<GroupDescriptor, Set<UsageDescriptor>>());

        final String result = ConvertUsagesUtil.convertUsages(patchedUsages);
        final Map<GroupDescriptor, Set<UsageDescriptor>> convertedUsages = ConvertUsagesUtil.convertString(result);

        assertMapEquals(patchedUsages, convertedUsages);
    }

    public void testConvertUsagesWithPriority() {
        final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = new HashMap<GroupDescriptor, Set<PatchedUsage>>();

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
        final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = new HashMap<GroupDescriptor, Set<PatchedUsage>>();

  createPatchDescriptor(patchedUsages, "g4", GroupDescriptor.HIGHER_PRIORITY, "1", 1);
        createPatchDescriptor(patchedUsages, "g2", GroupDescriptor.HIGHER_PRIORITY, "2", 1);
        createPatchDescriptor(patchedUsages, "g1", GroupDescriptor.HIGHER_PRIORITY, "3", 1);
        createPatchDescriptor(patchedUsages, "g3", GroupDescriptor.HIGHER_PRIORITY, "4", 1);


        assertEquals(ConvertUsagesUtil.convertUsages(patchedUsages), "g1:3=1;g2:2=1;g3:4=1;g4:1=1;");
    }

    public void testConvertString() {
        final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = new HashMap<GroupDescriptor, Set<PatchedUsage>>();

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

    public void testConvertWithTooLongGroupDescriptorId() {
      final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = new HashMap<GroupDescriptor, Set<PatchedUsage>>();
      createPatchDescriptor(patchedUsages, "g1", GroupDescriptor.HIGHER_PRIORITY, "k1", 1);
      createPatchDescriptor(patchedUsages, "g1", GroupDescriptor.HIGHER_PRIORITY, "k2", 2);

      final String veryLongGroupId = StringUtil.repeat("g", GroupDescriptor.MAX_ID_LENGTH);
      assertMapEquals(patchedUsages, ConvertUsagesUtil.convertString(veryLongGroupId + ":k1=1;g1:k1=1,k2=2;"));
    }

    public void testPersistSentPatchWithRestrictedSize() {
        int size = 15;
        final Map<GroupDescriptor, Set<UsageDescriptor>> allUsages = createDescriptors("g:a1:-1", "g2:a2:-2", "g2:a3:-3", "g:a2:-2", "g3:a1:-1", "g3:a2:13");

        final SentUsagesPersistence usagesPersistence = new BasicSentUsagesPersistenceComponent();
        Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        String first = StatisticsUploadAssistant.getStringPatch(patchedUsages, size);
        StatisticsUploadAssistant.persistSentPatch(first, usagesPersistence);

        assertTrue(first.length() <= size);
        assertMapEquals(ConvertUsagesUtil.convertString(first), ConvertUsagesUtil.convertString("g:a1=-1,a2=-2"));

        patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        String second = StatisticsUploadAssistant.getStringPatch(patchedUsages, size);
        StatisticsUploadAssistant.persistSentPatch(second, usagesPersistence);
        assertTrue(second.length() <= size);
        assertFalse(second.contains(first));
        assertMapEquals(ConvertUsagesUtil.convertString(second), ConvertUsagesUtil.convertString("g2:a2=-2,a3=-3"));

        patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        String third = StatisticsUploadAssistant.getStringPatch(patchedUsages, size);
        StatisticsUploadAssistant.persistSentPatch(third, usagesPersistence);
        assertTrue(third.length() <= size);
        assertFalse(third.contains(first));
        assertFalse(third.contains(second));
        assertMapEquals(ConvertUsagesUtil.convertString(third), ConvertUsagesUtil.convertString("g3:a1=-1,a2=13"));


        patchedUsages = StatisticsUploadAssistant.getPatchedUsages(allUsages, usagesPersistence);
        assertEquals(patchedUsages.size(), 0);

        assertEquals(allUsages.size(), usagesPersistence.getSentUsages().size());
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

    private static void createPatchDescriptor(Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages, String groupId, double priority, String key, int i) {
        final GroupDescriptor groupDescriptor = GroupDescriptor.create(groupId, priority);

        if (!patchedUsages.containsKey(groupDescriptor)){
            patchedUsages.put(groupDescriptor, new LinkedHashSet<PatchedUsage>());
        }
        patchedUsages.get(groupDescriptor).add(new PatchedUsage(key, i));
    }


    protected static UsageDescriptor createDescriptor(String k, int i) {
        return new UsageDescriptor(k, i);
    }

    protected static Map<GroupDescriptor, Set<UsageDescriptor>> createDescriptors(String... strs) {
        Map<GroupDescriptor, Set<UsageDescriptor>> set = new LinkedHashMap<GroupDescriptor, Set<UsageDescriptor>>();
        for (String str : strs) {
            final List<String> list = StringUtil.split(str, ":");
            final GroupDescriptor g = GroupDescriptor.create(list.get(0));
            if (!set.containsKey(g)) {
                set.put(g, new LinkedHashSet<UsageDescriptor>());
            }
            set.get(g).add(createDescriptor(list.get(1), Integer.parseInt(list.get(2))));
        }

        return set;
    }
}
