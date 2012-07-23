package org.jetbrains.android.compiler;

import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ResourceFileData;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class ResourceNamesValidityState implements ValidityState {
  private final Map<String, ResourceFileData> myResources = new HashMap<String, ResourceFileData>();

  private final String myAndroidTargetHashString;
  private final long myManifestTimestamp;

  public ResourceNamesValidityState(@NotNull Module module) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    final IAndroidTarget target = platform != null ? platform.getTarget() : null;
    myAndroidTargetHashString = target != null ? target.hashString() : "";

    final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(facet);
    myManifestTimestamp = manifestFile != null ? manifestFile.getModificationStamp() : -1;

    final LocalResourceManager manager = facet.getLocalResourceManager();

    for (final Pair<Resources, VirtualFile> pair : manager.getResourceElements()) {
      final Resources resources = pair.getFirst();
      final VirtualFile file = pair.getSecond();

      for (final ResourceType resType : AndroidResourceUtil.VALUE_RESOURCE_TYPES) {
        addValueResources(file, resType, AndroidResourceUtil.getValueResourcesFromElement(resType.getName(), resources), myResources, "");
      }
      addValueResources(file, ResourceType.ATTR, resources.getAttrs(), myResources, "");
      final List<DeclareStyleable> styleables = resources.getDeclareStyleables();
      addValueResources(file, ResourceType.DECLARE_STYLEABLE, styleables, myResources, "");

      for (DeclareStyleable styleable : styleables) {
        final String styleableName = styleable.getName().getValue();

        if (styleableName != null) {
          addValueResources(file, ResourceType.DECLARE_STYLEABLE, styleable.getAttrs(), myResources, styleableName);
        }
      }
    }

    manager.processFileResources(null, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
        final ResourceType type = ResourceType.getEnum(resFolderType);
        final boolean idProvidingResource = type != null && ArrayUtil.find(AndroidCommonUtils.ID_PROVIDING_RESOURCE_TYPES, type) >= 0;

        ResourceFileData data = myResources.get(resFile.getPath());
        if (data == null) {
          data = new ResourceFileData();
          myResources.put(resFile.getPath(), data);
        }

        if (idProvidingResource) {
          data.setTimestamp(resFile.getTimeStamp());
        }
        return true;
      }
    });
  }

  private static void addValueResources(VirtualFile file,
                                        ResourceType resType,
                                        Collection<? extends ResourceElement> resourceElements,
                                        Map<String, ResourceFileData> result,
                                        String context) {
    for (ResourceElement element : resourceElements) {
      final String name = element.getName().getValue();

      if (name != null) {
        ResourceFileData data = result.get(file.getPath());
        if (data == null) {
          data = new ResourceFileData();
          result.put(file.getPath(), data);
        }
        data.addValueResource(new ResourceEntry(resType.getName(), name, context));
      }
    }
  }

  public ResourceNamesValidityState(@NotNull DataInput in) throws IOException {
    myAndroidTargetHashString = in.readUTF();
    myManifestTimestamp = in.readLong();

    final int resourcesCount = in.readInt();

    for (int i = 0; i < resourcesCount; i++) {
      final String filePath = in.readUTF();

      final int valueResourcesCount = in.readInt();
      final List<ResourceEntry> valueResources = new ArrayList<ResourceEntry>(valueResourcesCount);

      for (int j = 0; j < valueResourcesCount; j++) {
        final String resType = in.readUTF();
        final String resName = in.readUTF();
        final String resContext = in.readUTF();
        valueResources.add(new ResourceEntry(resType, resName, resContext));
      }
      final long fileTimestamp = in.readLong();
      myResources.put(filePath, new ResourceFileData(valueResources, fileTimestamp));
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof ResourceNamesValidityState)) {
      return false;
    }

    final ResourceNamesValidityState other = (ResourceNamesValidityState)otherState;

    return other.myAndroidTargetHashString.equals(myAndroidTargetHashString) &&
           other.myManifestTimestamp == myManifestTimestamp &&
           other.myResources.equals(myResources);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myAndroidTargetHashString);
    out.writeLong(myManifestTimestamp);

    out.writeInt(myResources.size());

    for (Map.Entry<String, ResourceFileData> entry : myResources.entrySet()) {
      out.writeUTF(entry.getKey());

      final ResourceFileData data = entry.getValue();
      final List<ResourceEntry> valueResources = data.getValueResources();
      out.writeInt(valueResources.size());

      for (ResourceEntry resource : valueResources) {
        out.writeUTF(resource.getType());
        out.writeUTF(resource.getName());
        out.writeUTF(resource.getContext());
      }
      out.writeLong(data.getTimestamp());
    }
  }
}
