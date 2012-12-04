package com.intellij.openapi.util.io;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * @author yole
 */
public class UniqueNameBuilder<T> {
  private final Map<T, String> myPaths = new HashMap<T, String>();
  private final Map<T, String> myShortPaths = new HashMap<T, String>();
  private int myAbbreviateLevel = 1;
  private final String myRoot;
  private final String mySeparator;
  private final int myMaxLength;

  public UniqueNameBuilder(String root, String separator, int maxLength) {
    myRoot = root;
    mySeparator = separator;
    myMaxLength = maxLength;
  }

  public void addPath(T key, String value) {
    myPaths.put(key, value);
  }

  private void buildShortPaths() {
    while (true) {
      myShortPaths.clear();
      if (!reabbreviate()) break;
      myAbbreviateLevel++;
    }
  }

  private boolean reabbreviate() {
    Map<T, List<String>> segmentLists = new HashMap<T, List<String>>();
    for (Map.Entry<T, String> entry : myPaths.entrySet()) {
      List<String> segments = splitIntoSegments(entry.getValue());
      String shortPath = StringUtil.join(segments, mySeparator);
      if (shortPath.length() <= myMaxLength) {
        if (addShortPath(entry.getKey(), segments)) {
          return true;
        }
      }
      else {
        segmentLists.put(entry.getKey(), segments);
      }
    }
    truncateSegments(segmentLists);
    for (Map.Entry<T, List<String>> entry : segmentLists.entrySet()) {
      final List<String> segmentList = entry.getValue();
      Collections.reverse(segmentList);
      String shortPath = StringUtil.join(segmentList, mySeparator);
      if (myShortPaths.containsValue(shortPath)) {
        return true;
      }
      myShortPaths.put(entry.getKey(), shortPath);
    }
    return false;
  }

  private boolean addShortPath(final T key, List<String> segments) {
    String shortPath;
    Collections.reverse(segments);
    shortPath = StringUtil.join(segments, mySeparator);
    if (myShortPaths.containsValue(shortPath)) {
      return true;
    }
    myShortPaths.put(key, shortPath);
    return false;
  }

  private void truncateSegments(Map<T, List<String>> segmentLists) {
    for(int i=1; i<myAbbreviateLevel; i++) {
       tryTruncateSegments(segmentLists, i);
    }
  }

  private static final String NON_UNIQUE = "::::::";

  private void tryTruncateSegments(Map<T, List<String>> segmentLists, int index) {
    Map<String, String> truncatedSegments = ContainerUtil.newHashMap();
    for (Map.Entry<T, List<String>> entry : segmentLists.entrySet()) {
      if (entry.getValue().size() <= index+1) {
        continue;
      }
      String segment = entry.getValue().get(index);
      for (int i = 1; i < segment.length(); i++) {
        String truncated = segment.substring(0, i);
        String existing = truncatedSegments.get(truncated);
        if (existing == null) {
          truncatedSegments.put(truncated, segment);
          break;
        }
        else if (existing.equals(segment)) {
          break;
        }
        else if (!existing.equals(segment) && existing != NON_UNIQUE) {
          while (i < existing.length() && i < segment.length() && existing.charAt(i-1) == segment.charAt(i-1)) {
            truncatedSegments.put(segment.substring(0, i), NON_UNIQUE);
            //noinspection AssignmentToForLoopParameter
            i++;
          }
          if (i < existing.length()) {
            truncatedSegments.put(existing.substring(0, i), existing);
          }
          if (i < segment.length()) {
            truncatedSegments.put(segment.substring(0, i), segment);
          }
          break;
        }
      }
    }
    Map<String, String> inverted = ContainerUtil.newHashMap();
    for (Map.Entry<String, String> entry : truncatedSegments.entrySet()) {
      if (entry.getValue() != NON_UNIQUE) {
        inverted.put(entry.getValue(), entry.getKey());
      }
    }
    for (Map.Entry<T, List<String>> entry : segmentLists.entrySet()) {
      if (entry.getValue().size() <= index+1) {
        continue;
      }
      String segment = entry.getValue().get(index);
      String truncated = inverted.get(segment);
      if (truncated != null && truncated.length() < segment.length()) {
        entry.getValue().set(index, truncated + "\u2026");
      }
    }
  }

  private List<String> splitIntoSegments(String path) {
    int pos = path.lastIndexOf('/');
    if (pos < 0) return Collections.singletonList(path);
    List<String> segments = new ArrayList<String>();
    final String segment = path.substring(pos + 1);
    segments.add(segment);
    for (int i = 0; i < myAbbreviateLevel; i++) {
      int prevPos = path.lastIndexOf('/', pos-1);
      segments.add(path.substring(prevPos + 1, pos));
      pos = prevPos;
      if (pos < 0 || path.substring(0, pos).equals(myRoot)) break;
    }
    return segments;
  }

  public String getShortPath(T key) {
    if (myShortPaths.isEmpty()) {
      buildShortPaths();
    }
    return myShortPaths.get(key);
  }
}
