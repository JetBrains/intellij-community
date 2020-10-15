// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.With;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Index of EPs via associated class.
 * <p>
 * Stores either {@link ExtensionPoint#getInterface()},<br/>
 * <b>or</b>
 * <ul>
 *   <li>{@link ExtensionPoint#getBeanClass()}</li>
 *   <li>all {@link With#getImplements()}</li>
 * </ul>
 * </p>
 *
 * @see org.jetbrains.idea.devkit.util.ExtensionPointLocator
 */
public class ExtensionPointClassIndex extends PluginXmlIndexBase<String, IntList> {

  private static final ID<String, IntList> NAME = ID.create("devkit.ExtensionPointClassIndex");

  /**
   * for n > 1: list of offsets
   * for n = 1: -offset (most common case)
   */
  private final DataExternalizer<IntList> myValueExternalizer = new DataExternalizer<>() {
    @Override
    public void save(@NotNull final DataOutput out, final IntList values) throws IOException {
      final int size = values.size();
      if (size == 1) {
        DataInputOutputUtil.writeINT(out, -values.getInt(0));
        return;
      }

      DataInputOutputUtil.writeINT(out, size);
      for (int i = 0; i < size; ++i) {
        DataInputOutputUtil.writeINT(out, values.getInt(i));
      }
    }

    @Override
    public IntList read(@NotNull final DataInput in) throws IOException {
      int count = DataInputOutputUtil.readINT(in);
      if (count < 0) {
        return new IntArrayList(new int[]{-count});
      }

      IntList result = new IntArrayList(count);
      for (int i = 0; i < count; i++) {
        result.add(DataInputOutputUtil.readINT(in));
      }

      return result;
    }
  };

  @NotNull
  @Override
  public DataExternalizer<IntList> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  protected Map<String, IntList> performIndexing(IdeaPlugin plugin) {
    final Map<String, IntList> result = FactoryMap.create(key -> new IntArrayList());
    ExtensionPointIndex.indexExtensionPoints(plugin, point -> {
      int offset = point.getXmlTag().getTextOffset();
      if (addToIndex(result, point.getInterface(), offset)) return;

      addToIndex(result, point.getBeanClass(), offset);
      for (With element : point.getWithElements()) {
        addToIndex(result, element.getImplements(), offset);
      }
    });
    return result;
  }

  private static boolean addToIndex(Map<String, IntList> map, GenericAttributeValue<PsiClass> value, int offset) {
    if (!DomUtil.hasXml(value)) return false;
    map.get(value.getStringValue()).add(offset);
    return true;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public ID<String, IntList> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  public static List<ExtensionPoint> getExtensionPointsByClass(Project project, PsiClass psiClass, GlobalSearchScope scope) {
    final String key = ClassUtil.getJVMClassName(psiClass);
    if (key == null) return Collections.emptyList();

    List<ExtensionPoint> result = new SmartList<>();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final DomManager domManager = DomManager.getDomManager(project);

    FileBasedIndex.getInstance().processValues(NAME, key, null, (file, value) -> {
      for (Integer integer : value) {
        result.add(ExtensionPointIndex.getExtensionPointDom(psiManager, domManager, file, integer));
      }
      return true;
    }, scope);
    return result;
  }
}
