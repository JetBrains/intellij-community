package org.jetbrains.idea.eclipse.util;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class PathUtil {

  public static final String UNRESOLVED_PREFIX = "?";
  public static final String HTTP_PREFIX = "http://";
  public static final String HTTPS_PREFIX = "https://";

  public static String normalizeSlashes(String path) {
    return path.replace("\\", "/");
  }

  public static String normalize(String path) {
    path = normalizeSlashes(path);
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    while (path.contains("/./")) {
      path = path.replace("/./", "/");
    }
    if (path.startsWith("./")) {
      path = path.substring(2);
    }
    if (path.endsWith("/.")) {
      path = path.substring(0, path.length() - 2);
    }

    while ( true ) {
      int index = path.indexOf("/..");
      if ( index < 0 ) break;
      int slashIndex = path.substring(0,index).lastIndexOf("/");
      if ( slashIndex < 0 ) break;
      path = path.substring(0, slashIndex ) + path.substring(index+3);
    }

    return path;
  }

  public static boolean isAbsolute(String path) {
    return new File(path).isAbsolute();
  }

  public static String concatPath(String baseRoot, String path) {
    if (isAbsolute(path)) {
      return path;
    } else {
      return normalize(baseRoot + "/" + path);
    }
  }

  public static String rebase(String path, String oldRoot, String newRoot) {
    path = normalize(path);
    oldRoot = normalize(oldRoot);
    if (path.equals(oldRoot)) {
      return normalize(newRoot);
    }
    if ( path.startsWith ( oldRoot +"/")) {
      return concatPath(newRoot, path.substring(oldRoot.length()+1));
    }
    return path;
  }

  public static boolean isUnder(String root, String path) {
    path = normalize(path);
    root = normalize(root);
    return path.equals(root) || path.startsWith(root+"/");
  }

  public static String getRelative(String baseRoot, String path) {
    baseRoot = PathUtil.normalize(baseRoot);
    path = PathUtil.normalize(path);

    int prefix = findCommonPathPrefixLength(baseRoot, path);

    if (prefix != 0) {
      baseRoot = baseRoot.substring(prefix);
      path = path.substring(prefix);
      if (baseRoot.length() != 0) {
        return normalize(revertRelativePath(baseRoot.substring(1)) + path);
      }
      else if (path.length() != 0) {
        return path.substring(1);
      }
      else {
        return ".";
      }
    }
    else if (isAbsolute(path)) {
      return path;
    }
    else {
      return normalize(revertRelativePath(baseRoot) + "/" + path);
    }
  }

  public static int findCommonPathPrefixLength(String path1, String path2) {
    int end = -1;
    do {
      int beg = end + 1;
      int new_end = endOfToken(path1, beg);
      if (new_end != endOfToken(path2, beg) || !path1.substring(beg, new_end).equals(path2.substring(beg, new_end))) {
        break;
      }
      end = new_end;
    }
    while (end != path1.length());
    return end < 0 ? 0 : end;
  }

  private static int endOfToken(String s, int index) {
    index = s.indexOf("/", index);
    return (index == -1) ? s.length() : index;
  }

  private static String revertRelativePath(String path) {
    if (path.equals(".")) {
      return path;
    }
    else {
      StringBuffer sb = new StringBuffer();
      sb.append("..");
      int count = normalize(path).split("/").length;
      while (--count > 0) {
        sb.append("/..");
      }
      return sb.toString();
    }
  }

  public static String getContainerName(String path) {
    String[] tokens = path.split("/");
    return tokens.length > 1 ? tokens[tokens.length - 1] : null;
  }

  public static boolean isWeb(String path) {
    return path.startsWith(HTTP_PREFIX) || path.startsWith(HTTPS_PREFIX);
  }

  public interface RootFinder {
    @Nullable
    String getRootByName(String name);
  }

  public static String convertToRelative(RootFinder rootFinder, String baseRoot, String path) {
    if (path.startsWith("/")) {
      if (rootFinder != null) {
        int moduleNameEnd = endOfToken(path, 1);
        String otherRoot = rootFinder.getRootByName(path.substring(1, moduleNameEnd));
        if (otherRoot != null) {
          return getRelative(baseRoot, otherRoot + path.substring(moduleNameEnd));
        }
      }
      return UNRESOLVED_PREFIX + path.substring(1);
    }

    if (isAbsolute(path)) {
      try {
        return getRelative(normalize(new File(baseRoot).getCanonicalPath()), normalize(new File(path).getCanonicalPath()));
      }
      catch (IOException e) {
        return path;
      }
    }

    return path;
  }

  public static boolean isUnresolved ( final String path ) {
    return path.startsWith(UNRESOLVED_PREFIX);
  }

}
