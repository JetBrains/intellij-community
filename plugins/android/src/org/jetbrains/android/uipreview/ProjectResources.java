package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.InlineResourceItem;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
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
  private final static int DYNAMIC_ID_SEED_START = 0x7fff0000;

  private Map<ResourceType, TObjectIntHashMap<String>> mResourceValueMap;
  private TIntObjectHashMap<Pair<ResourceType, String>> mResIdValueToNameMap;
  private Map<IntArrayWrapper, String> mStyleableValueToNameMap;

  private final TObjectIntHashMap<String> mDynamicIds = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<String> mRevDynamicIds = new TIntObjectHashMap<String>();
  private int mDynamicSeed = DYNAMIC_ID_SEED_START;

  public ProjectResources() {
    super(false);
  }

  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (mResIdValueToNameMap != null) {
      result = mResIdValueToNameMap.get(id);
    }

    if (result == null) {
      String name = mRevDynamicIds.get(id);
      if (name != null) {
        result = Pair.of(ResourceType.ID, name);
      }
    }

    return result;
  }

  @Nullable
  public String resolveStyleable(int[] id) {
    if (mStyleableValueToNameMap != null) {
      mWrapper.set(id);
      return mStyleableValueToNameMap.get(mWrapper);
    }

    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    if (mResourceValueMap != null) {
      final TObjectIntHashMap<String> map = mResourceValueMap.get(type);
      if (map != null) {
        if (!map.contains(name)) {
          if (ResourceType.ID == type) {
            return getDynamicId(name);
          }
          return null;
        }

        return map.get(name);
      }
      else if (ResourceType.ID == type) {
        return getDynamicId(name);
      }
    }

    return null;
  }

  @Override
  protected ResourceItem createResourceItem(String name) {
    return new ResourceItem(name);
  }

  private int getDynamicId(String name) {
    synchronized (mDynamicIds) {
      if (mDynamicIds.containsKey(name)) {
        return mDynamicIds.get(name);
      }
      final int value = ++mDynamicSeed;
      mDynamicIds.put(name, value);
      mRevDynamicIds.put(value, name);
      return value;
    }
  }

  void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                            Map<IntArrayWrapper, String> styleableid2name,
                            Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    mResourceValueMap = res2id;
    mResIdValueToNameMap = id2res;
    mStyleableValueToNameMap = styleableid2name;
    mergeIdResources();
  }

  @Override
  protected void postUpdate() {
    super.postUpdate();
    mergeIdResources();
  }

  private void mergeIdResources() {
    if (mResourceValueMap == null) {
      return;
    }

    List<ResourceItem> resources = mResourceMap.get(ResourceType.ID);
    final TObjectIntHashMap<String> name2id = mResourceValueMap.get(ResourceType.ID);

    if (name2id != null) {
      final TObjectIntHashMap<String> copy;

      if (resources == null) {
        resources = new ArrayList<ResourceItem>(name2id.size());
        mResourceMap.put(ResourceType.ID, resources);
        copy = name2id;
      }
      else {
        copy = new TObjectIntHashMap<String>(name2id);

        int i = 0;
        while (i < resources.size()) {
          ResourceItem item = resources.get(i);
          String name = item.getName();
          if (item.isDeclaredInline()) {
            if (copy.contains(name)) {
              copy.remove(name);
              i++;
            }
            else {
              resources.remove(i);
            }
          }
          else {
            copy.remove(name);
            i++;
          }
        }
      }

      for (Object name : copy.keys()) {
        resources.add(new InlineResourceItem((String)name));
      }
    }
  }
}
