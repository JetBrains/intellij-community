// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.concurrency.ConcurrencyUtils;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Index of extension registrations by class names and short names of extension points.
 * <p>
 * Each class-like attribute value or leaf tag text under {@code <extensions>} is a key. This includes nested tags.
 * Each value stores tag offsets shifted left by one bit. The low bit marks matches that contain only an outer-class prefix.
 * For a binary name {@code com.foo.Outer$Inner} the prefix before each {@code $} ({@code com.foo.Outer}) is a key as well.
 * Keys with the {@code ep:} prefix store the last segment of each extension tag name.
 * </p>
 * <p>
 * The indexer resolves nothing: it does not know the extension point, so it over-approximates. Callers must filter the returned
 * tags via DOM (e.g. {@code Extension.getExtensionPoint()}).
 * </p>
 *
 * @see ExtensionPointClassIndex
 * @see org.jetbrains.idea.devkit.util.ExtensionLocator
 */
public final class ExtensionImplementationIndex extends PluginXmlIndexBase<String, IntList> {

  static final ID<String, IntList> NAME = ID.create("devkit.ExtensionImplementationIndex");
  private static final String EP_NAME_KEY_PREFIX = "ep:";
  private static final int MIN_PARALLEL_EP_OFFSETS = 256;

  private final DataExternalizer<IntList> myValueExternalizer = new DataExternalizer<>() {
    @Override
    public void save(final @NotNull DataOutput out, final IntList values) throws IOException {
      final int size = values.size();
      DataInputOutputUtil.writeINT(out, size);
      for (int i = 0; i < size; ++i) {
        DataInputOutputUtil.writeINT(out, values.getInt(i));
      }
    }

    @Override
    public IntList read(final @NotNull DataInput in) throws IOException {
      int count = DataInputOutputUtil.readINT(in);
      IntList result = new IntArrayList(count);
      for (int i = 0; i < count; i++) {
        result.add(DataInputOutputUtil.readINT(in));
      }

      return result;
    }
  };

  @Override
  public @NotNull ID<String, IntList> getName() {
    return NAME;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<IntList> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public int getVersion() {
    return BASE_INDEX_VERSION + 3;
  }

  @Override
  protected Map<String, IntList> performIndexing(IdeaPlugin plugin) {
    Map<String, IntList> result = new HashMap<>();
    for (Extensions extensions : plugin.getExtensions()) {
      for (XmlTag extensionTag : extensions.getXmlTag().getSubTags()) {
        var offset = extensionTag.getTextOffset();
        addKey(result, EP_NAME_KEY_PREFIX + StringUtil.getShortName(extensionTag.getLocalName()), offset, false);
        collectKeys(extensionTag, offset, result);
      }
    }
    return result;
  }

  private static void collectKeys(XmlTag tag, int offset, Map<String, IntList> result) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      addKeys(result, attribute.getValue(), offset);
    }

    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length == 0) {
      addKeys(result, tag.getValue().getTrimmedText(), offset);
      return;
    }
    for (XmlTag subTag : subTags) {
      collectKeys(subTag, offset, result);
    }
  }

  private static void addKeys(Map<String, IntList> result, @Nullable String value, int offset) {
    if (value == null || !isClassLike(value)) return;

    addKey(result, value, offset, false);
    int dollar = value.indexOf('$');
    while (dollar > 0) {
      addKey(result, value.substring(0, dollar), offset, true);
      dollar = value.indexOf('$', dollar + 1);
    }
  }

  private static void addKey(Map<String, IntList> result, String key, int offset, boolean prefixOnly) {
    var offsets = result.computeIfAbsent(key, _ -> new IntArrayList(1));
    var encodedOffset = (offset << 1) | (prefixOnly ? 1 : 0);
    var last = offsets.size() - 1;
    if (last < 0 || (offsets.getInt(last) >>> 1) != offset) {
      offsets.add(encodedOffset);
    }
    else if (!prefixOnly) {
      offsets.set(last, encodedOffset);
    }
  }

  /**
   * Matches {@code ^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)+$} with at least one segment starting with an uppercase letter.
   * Values that pass are keys of this index; queries for such values must use it instead of a word search.
   */
  @ApiStatus.Internal
  public static boolean isClassLike(@NotNull String value) {
    boolean hasDot = false;
    boolean hasUpperSegment = false;
    boolean segmentStart = true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '.') {
        if (segmentStart) return false;
        hasDot = true;
        segmentStart = true;
        continue;
      }

      boolean isLetter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
      if (segmentStart) {
        if (!isLetter && c != '_' && c != '$') return false;
        if (c >= 'A' && c <= 'Z') hasUpperSegment = true;
        segmentStart = false;
      }
      else if (!isLetter && c != '_' && c != '$' && !(c >= '0' && c <= '9')) {
        return false;
      }
    }
    return hasDot && !segmentStart && hasUpperSegment;
  }

  /**
   * Feeds the extension tags registered with {@code binaryClassName} (as written, or as the outer class of a {@code $}-separated inner
   * class name) to {@code processor}. Tags are not filtered by extension point.
   *
   * @return {@code false} if {@code processor} stopped the iteration
   */
  public static boolean processExtensions(@NotNull Project project,
                                          @NotNull String binaryClassName,
                                          @NotNull GlobalSearchScope scope,
                                          @NotNull Processor<? super XmlTag> processor) {
    return processExtensions(project, binaryClassName, scope, false, processor);
  }

  /**
   * Processes matching tags. If {@code strictMatch} is true, skips tags that mention only an inner class of the requested class.
   *
   * @return {@code false} if {@code processor} stopped the iteration
   */
  public static boolean processExtensions(@NotNull Project project,
                                          @NotNull String binaryClassName,
                                          @NotNull GlobalSearchScope scope,
                                          boolean strictMatch,
                                          @NotNull Processor<? super XmlTag> processor) {
    if (StringUtil.isEmpty(binaryClassName)) return true;
    return processTags(project, binaryClassName, scope, strictMatch, processor);
  }

  /**
   * Processes extension tags whose last name segment equals {@code shortName}.
   * Callers must check the full name of the extension point through DOM.
   * The processor can run concurrently for different files. Use a thread-safe accumulator.
   *
   * @return {@code false} if {@code processor} stopped the iteration
   */
  public static boolean processExtensionsByEpShortName(@NotNull Project project,
                                                       @NotNull String shortName,
                                                       @NotNull GlobalSearchScope scope,
                                                       @NotNull Processor<? super XmlTag> processor) {
    if (StringUtil.isEmpty(shortName)) return true;
    var candidates = new ArrayList<FileOffsets>();
    if (!FileBasedIndex.getInstance().processValues(NAME, EP_NAME_KEY_PREFIX + shortName, null, (file, offsets) -> {
      candidates.add(new FileOffsets(file, offsets));
      return true;
    }, scope)) return false;

    var psiManager = PsiManager.getInstance(project);
    int offsetCount = 0;
    for (var candidate : candidates) {
      offsetCount += candidate.offsets().size();
      if (offsetCount >= MIN_PARALLEL_EP_OFFSETS) break;
    }
    if (candidates.size() <= 1 || offsetCount < MIN_PARALLEL_EP_OFFSETS) {
      return ContainerUtil.process(candidates, candidate -> processFile(psiManager, candidate, true, processor));
    }
    var stopped = new AtomicBoolean();
    Processor<XmlTag> concurrentProcessor = tag -> {
      if (stopped.get()) return false;
      if (processor.process(tag)) return true;
      stopped.set(true);
      return false;
    };
    return ConcurrencyUtils.runWithIndicatorOrContextCancellation(_ ->
      JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress(candidates, ReadActionProcessor.wrapInReadAction(candidate ->
        !stopped.get() && processFile(psiManager, candidate, true, concurrentProcessor))));
  }

  private record FileOffsets(VirtualFile file, IntList offsets) { }

  private static boolean processTags(@NotNull Project project,
                                     @NotNull String key,
                                     @NotNull GlobalSearchScope scope,
                                     boolean strictMatch,
                                     @NotNull Processor<? super XmlTag> processor) {
    PsiManager psiManager = PsiManager.getInstance(project);
    return FileBasedIndex.getInstance().processValues(NAME, key, null,
      (file, offsets) -> processFile(psiManager, new FileOffsets(file, offsets), strictMatch, processor), scope);
  }

  private static boolean processFile(PsiManager psiManager,
                                     FileOffsets candidate,
                                     boolean strictMatch,
                                     Processor<? super XmlTag> processor) {
    PsiFile psiFile = null;
    var offsets = candidate.offsets();
    for (int i = 0; i < offsets.size(); i++) {
      ProgressManager.checkCanceled();
      var encodedOffset = offsets.getInt(i);
      if (strictMatch && (encodedOffset & 1) != 0) continue;
      if (psiFile == null) {
        psiFile = psiManager.findFile(candidate.file());
        if (!(psiFile instanceof XmlFile)) return true;
      }
      XmlTag tag = getTagAt(psiFile, encodedOffset >>> 1);
      if (tag != null && !processor.process(tag)) return false;
    }
    return true;
  }

  private static @Nullable XmlTag getTagAt(PsiFile psiFile, int offset) {
    return AstLoadingFilter.forceAllowTreeLoading(psiFile, () -> {
      PsiElement psiElement = psiFile.findElementAt(offset);
      return PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
    });
  }
}
