// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InlineBreakpointInlayManager {

  // FIXME[inline-bp]: it's super inefficient
  static void redrawInlineBreakpoints(XLineBreakpointManager lineBreakpointManager, @NotNull Project project, @NotNull VirtualFile file, @NotNull Document document) {
    if (!Registry.is("debugger.show.breakpoints.inline")) return;

    Collection<XLineBreakpointImpl> allBreakpoints = lineBreakpointManager.getDocumentBreakpoints(document);
    Map<Integer, List<XLineBreakpointImpl>> breakpointsByLine = allBreakpoints.stream().collect(Collectors.groupingBy(b -> b.getLine()));

    // FIXME[inline-bp]: it's super inefficient
    // remove all previous inlays
    for (var editor : EditorFactory.getInstance().getEditors(document, project)) {
      var inlayModel = editor.getInlayModel();
      var inlays = inlayModel.getInlineElementsInRange(Integer.MIN_VALUE, Integer.MAX_VALUE, InlineBreakpointInlayRenderer.class);
      inlays.forEach(Disposable::dispose);
    }

    for (Map.Entry<Integer, List<XLineBreakpointImpl>> entry : breakpointsByLine.entrySet()) {
      var line = entry.getKey();
      List<XLineBreakpointImpl> breakpoints = entry.getValue();

      if (line < 0) continue;

      var linePosition = XSourcePositionImpl.create(file, line);
      List<XLineBreakpointType> breakpointTypes;
      try (var ignore = SlowOperations.knownIssue("IDEA-333520, EA-908835")) {
        breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null);
      }
      XDebuggerUtilImpl.getLineBreakpointVariants(project, breakpointTypes, linePosition).onProcessed(variants -> {
        if (variants == null) {
          variants = Collections.emptyList();
        } else if (ContainerUtil.exists(variants, InlineBreakpointInlayManager::isAllVariant)) {
          // No need to show "all" variant in case of the inline breakpoints approach, it's useful only for the popup based one.
          variants = variants.stream().filter(v -> !isAllVariant(v)).toList();
        }

        var codeStartOffset = DocumentUtil.getLineStartIndentedOffset(document, line);

        if (breakpoints.size() == 1 && variants.size() == 1 &&
            areMatching(variants.get(0), breakpoints.get(0), codeStartOffset)) {
          // No need to show inline variants when there is only one breakpoint and one matching variant.
          return;
        }

        for (var variant : variants) {
          var breakpointsHere = ContainerUtil.findAll(breakpoints, b -> areMatching(variant, b, codeStartOffset));
          if (!breakpointsHere.isEmpty()) {
            for (XLineBreakpointImpl breakpointHere : breakpointsHere) {
              breakpoints.remove(breakpointHere);
              EditorFactory.getInstance().editors(document, project).forEach(editor -> {
                addInlineBreakpointInlay(editor, breakpointHere, variant, codeStartOffset);
              });
            }
          } else {
            EditorFactory.getInstance().editors(document, project).forEach(editor -> {
              addInlineBreakpointInlay(editor, variant, codeStartOffset);
            });
          }
        }

        for (XLineBreakpointImpl remainingBreakpoint : breakpoints) {
          EditorFactory.getInstance().editors(document, project).forEach(editor -> {
            addInlineBreakpointInlay(editor, remainingBreakpoint, null, codeStartOffset);
          });
        }
      });
    }
  }

  private static boolean isAllVariant(XLineBreakpointType<?>.XLineBreakpointVariant variant) {
    // Currently, it's the easiest way to check that it's really multi-location variant.
    // Don't try to check whether the variant is an instance of XLineBreakpointAllVariant, they all are.
    // FIXME[inline-bp]: introduce better way for this or completely get rid of multi-location variants
    return variant.getIcon() == AllIcons.Debugger.MultipleBreakpoints;
  }

  private static void addInlineBreakpointInlay(Editor editor, @NotNull XLineBreakpointImpl<?> breakpoint, @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var offset = getBreakpointRangeStartOffset(breakpoint, codeStartOffset);
    addInlineBreakpointInlayImpl(editor, offset, breakpoint, variant);
  }

  private static void addInlineBreakpointInlay(Editor editor, @NotNull XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var offset = getBreakpointVariantRangeStartOffset(variant, codeStartOffset);
    addInlineBreakpointInlayImpl(editor, offset, null, variant);
  }

  private static void addInlineBreakpointInlayImpl(Editor editor, int offset, @Nullable XLineBreakpointImpl<?> breakpoint, @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant) {
    var inlayModel = editor.getInlayModel();
    var renderer = new InlineBreakpointInlayRenderer(breakpoint, variant);
    var inlay = inlayModel.addInlineElement(offset, renderer);
    renderer.setInlay(inlay);
  }

  private static boolean areMatching(XLineBreakpointType<?>.XLineBreakpointVariant variant, XLineBreakpointImpl<?> breakpoint, int codeStartOffset) {
    return variant.getType().equals(breakpoint.getType()) &&
           getBreakpointVariantRangeStartOffset(variant, codeStartOffset) == getBreakpointRangeStartOffset(breakpoint, codeStartOffset);
  }

  private static int getBreakpointVariantRangeStartOffset(XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var variantRange = variant.getHighlightRange();
    return getLineRangeStartNormalized(variantRange, codeStartOffset);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int getBreakpointRangeStartOffset(XLineBreakpointImpl<?> breakpoint, int codeStartOffset) {
    var breakpointRange = breakpoint.getType().getHighlightRange((XLineBreakpointImpl)breakpoint);
    return getLineRangeStartNormalized(breakpointRange, codeStartOffset);
  }

  private static int getLineRangeStartNormalized(TextRange range, int codeStartOffset) {
    // Null range represents the whole line.
    // Any start offset from the line start until first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character for ease of comparison of various ranges coming from variants and breakpoints.
    return range != null ? Math.max(range.getStartOffset(), codeStartOffset) : codeStartOffset;
  }
}
