// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.impl.local.windows.WindowsBufferedDirectoryStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/// Iterative (explicit-stack) equivalent of [Files#walkFileTree] that sources directory entries from the
/// Windows-native [WindowsBufferedDirectoryStream]. Using a heap-allocated stack instead of the call stack avoids
/// [StackOverflowError] on very deep directory trees.
/// Reproduces [Files#walkFileTree]'s no-options semantics (NOFOLLOW_LINKS, unbounded depth) and visitor contract.
@ApiStatus.Internal
final class WindowsBufferedFileTreeWalker {
  private final FileVisitor<Path> myVisitor;
  private final Deque<DirectoryFrame> myStack = new ArrayDeque<>();

  private WindowsBufferedFileTreeWalker(@NotNull FileVisitor<Path> visitor) {
    myVisitor = visitor;
  }

  static void walk(@NotNull Path root, @NotNull FileVisitor<Path> visitor) throws IOException {
    new WindowsBufferedFileTreeWalker(visitor).walk(root);
  }

  /// One open directory on the walk stack: its path, the backing stream and a lazily advanced iterator over its entries.
  private static final class DirectoryFrame {
    final Path dir;
    final WindowsBufferedDirectoryStream stream;
    final Iterator<Pair<Path, BasicFileAttributes>> iterator;
    /// Set by a {@code SKIP_SIBLINGS} result to stop iterating this directory's remaining entries (postVisit still runs).
    boolean skipRemaining;

    DirectoryFrame(@NotNull Path dir, @NotNull WindowsBufferedDirectoryStream stream) {
      this.dir = dir;
      this.stream = stream;
      this.iterator = stream.iterator();
    }
  }

  private void walk(@NotNull Path root) throws IOException {
    BasicFileAttributes rootAttrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

    try {
      // The root has no parent, so its pre/postVisit result is ignored - exactly as Files.walkFileTree ignores it.
      enterDirectory(root, rootAttrs);

      while (!myStack.isEmpty()) {
        DirectoryFrame top = myStack.peek();
        Pair<Path, BasicFileAttributes> entry = nextEntry(top);

        if (entry == null) {
          // Directory exhausted (or skipped): close it, run postVisitDirectory, then bubble the result to its parent.
          // 'pending' is always null here: an open failure is handled in enterDirectory and an iteration failure
          // throws a (runtime) DirectoryIteratorException that unwinds the stack instead of reaching this point.
          myStack.pop();
          top.stream.close();
          if (!bubbleToParent(myVisitor.postVisitDirectory(top.dir, null))) return;  // TERMINATE
          continue;
        }

        Path child = entry.getFirst();
        BasicFileAttributes attrs = entry.getSecond();

        FileVisitResult result;
        if (attrs.isDirectory()) {
          result = enterDirectory(child, attrs);
          if (result == null) continue;  // descended into the child; it is iterated on the next turn
        }
        else {
          result = myVisitor.visitFile(child, attrs);
        }

        // 'result' applies to 'top' - the directory currently being iterated.
        if (result == FileVisitResult.TERMINATE) return;
        if (result == FileVisitResult.SKIP_SIBLINGS) top.skipRemaining = true;
        // CONTINUE / SKIP_SUBTREE: keep iterating the current directory
      }
    }
    finally {
      // Close every directory still open - on TERMINATE or while an exception unwinds the stack - mirroring how
      // Files.walkFileTree's try-with-resources releases each level without running postVisitDirectory for it.
      closeAll();
    }
  }

  /// Runs {@code preVisitDirectory} and, when it returns {@code CONTINUE}, opens {@code dir} and pushes a frame.
  ///
  /// @return {@code null} when a frame was pushed (the walk descended into {@code dir}); otherwise the result the caller
  /// must bubble up: {@code preVisitDirectory}'s own value for {@code TERMINATE}/{@code SKIP_SIBLINGS}, {@code CONTINUE}
  /// for {@code SKIP_SUBTREE}, or {@code postVisitDirectory}'s value when the directory failed to open.
  private @Nullable FileVisitResult enterDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
    FileVisitResult pre = myVisitor.preVisitDirectory(dir, attrs);
    if (pre == FileVisitResult.SKIP_SUBTREE) return FileVisitResult.CONTINUE;
    if (pre != FileVisitResult.CONTINUE) return pre;  // TERMINATE / SKIP_SIBLINGS bubble up to the caller

    WindowsBufferedDirectoryStream stream;
    try {
      stream = new WindowsBufferedDirectoryStream(dir);
    }
    catch (IOException e) {
      return myVisitor.postVisitDirectory(dir, e);  // directory open failure -> reported through postVisitDirectory, as Files.walkFileTree does
    }
    myStack.push(new DirectoryFrame(dir, stream));
    return null;
  }

  /// @return the next eligible entry of {@code frame}, or {@code null} when the directory is skipped or exhausted.
  private static @Nullable Pair<Path, BasicFileAttributes> nextEntry(@NotNull DirectoryFrame frame) {
    if (frame.skipRemaining || !frame.iterator.hasNext()) return null;
    return frame.iterator.next();
  }

  /// Applies a {@code postVisitDirectory} result to the parent directory still on the stack.
  /// @return {@code false} when the walk must terminate, {@code true} to continue.
  private boolean bubbleToParent(@NotNull FileVisitResult result) {
    if (result == FileVisitResult.TERMINATE) return false;
    DirectoryFrame parent = myStack.peek();
    if (result == FileVisitResult.SKIP_SIBLINGS && parent != null) parent.skipRemaining = true;
    return true;
  }

  private void closeAll() {
    while (!myStack.isEmpty()) {
      myStack.pop().stream.close();
    }
  }
}
