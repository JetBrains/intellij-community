package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.InlineResourceItem;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.io.IAbstractFolder;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class ProjectResources extends ResourceRepository {
  // project resources are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;

  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<String> myName2DynamicIdMap = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<Pair<ResourceType, String>> myDynamicId2ResourceMap = 
    new TIntObjectHashMap<Pair<ResourceType, String>>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;

  private final List<ProjectResources> myLibResources;

  public ProjectResources(@NotNull IAbstractFolder resFolder, @NotNull List<ProjectResources> libResources) {
    super(resFolder, false);
    myLibResources = libResources;
  }

  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (myResIdValueToNameMap != null) {
      result = myResIdValueToNameMap.get(id);
    }

    if (result == null) {
      final Pair<ResourceType, String> pair = myDynamicId2ResourceMap.get(id);
      if (pair != null) {
        result = pair;
      }
    }

    return result;
  }

  @Nullable
  public String resolveStyleable(int[] id) {
    if (myStyleableValueToNameMap != null) {
      mWrapper.set(id);
      return myStyleableValueToNameMap.get(mWrapper);
    }

    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;
    if (map != null) {
      if (!map.contains(name)) {
        if (ResourceType.ID == type || ResourceType.LAYOUT == type) {
          return getDynamicId(type, name);
        }
        return null;
      }

      return map.get(name);
    }
    else if (ResourceType.ID == type || ResourceType.LAYOUT == type) {
      return getDynamicId(type, name);
    }

    return null;
  }

  @Override
  protected ResourceItem createResourceItem(String name) {
    return new ResourceItem(name);
  }

  private int getDynamicId(ResourceType type, String name) {
    synchronized (myName2DynamicIdMap) {
      if (myName2DynamicIdMap.containsKey(name)) {
        return myName2DynamicIdMap.get(name);
      }
      final int value = ++myDynamicSeed;
      myName2DynamicIdMap.put(name, value);
      myDynamicId2ResourceMap.put(value, Pair.of(type, name));
      return value;
    }
  }

  void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                            Map<IntArrayWrapper, String> styleableid2name,
                            Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableid2name;
    mergeIdResources();
  }

  @Override
  public void postUpdateCleanUp() {
    super.postUpdateCleanUp();
    mergeIdResources();
  }

  private void mergeIdResources() {
    if (myResourceValueMap == null) {
      return;
    }

    Map<String, ResourceItem> resources = mResourceMap.get(ResourceType.ID);
    final TObjectIntHashMap<String> name2id = myResourceValueMap.get(ResourceType.ID);

    if (name2id != null) {
      final TObjectIntHashMap<String> copy;

      if (resources == null) {
        resources = new HashMap<String, ResourceItem>(name2id.size());
        mResourceMap.put(ResourceType.ID, resources);
        copy = name2id;
      }
      else {
        copy = new TObjectIntHashMap<String>(name2id);

        final List<ResourceItem> resList = new ArrayList<ResourceItem>(resources.values());
        for (ResourceItem item : resList) {
          String name = item.getName();
          if (item.isDeclaredInline()) {
            if (copy.contains(name)) {
              copy.remove(name);
            }
            else {
              resources.remove(item.getName());
            }
          }
          else {
            copy.remove(name);
          }
        }
      }

      for (Object name : copy.keys()) {
        resources.put((String)name, new InlineResourceItem((String)name));
      }
    }
  }

  @Override
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredResources(FolderConfiguration referenceConfig) {
    final Map<ResourceType, Map<String, ResourceValue>> result = new HashMap<ResourceType, Map<String, ResourceValue>>();

    for (int i = myLibResources.size() - 1; i >= 0; i--) {
      putAllResourceEntries(result, myLibResources.get(i).doGetConfiguredResources(referenceConfig));
    }
    putAllResourceEntries(result, doGetConfiguredResources(referenceConfig));
    return result;
  }

  private static void putAllResourceEntries(Map<ResourceType, Map<String, ResourceValue>> result,
                                            Map<ResourceType, Map<String, ResourceValue>> from) {
    for (Map.Entry<ResourceType, Map<String, ResourceValue>> entry : from.entrySet()) {
      Map<String, ResourceValue> map = result.get(entry.getKey());

      if (map == null) {
        map = new HashMap<String, ResourceValue>();
        result.put(entry.getKey(), map);
      }
      map.putAll(entry.getValue());
    }
  }
}
