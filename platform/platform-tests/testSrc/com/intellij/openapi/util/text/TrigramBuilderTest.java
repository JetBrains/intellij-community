/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class TrigramBuilderTest {
  public static void main(String[] args) throws IOException {
    File root = new File(args[0]);

    Stats stats = new Stats();
    walk(root, stats);

    System.out.println("Scanned " + stats.files + " files, total of " + stats.lines + " lines in " + (stats.time / 1000000) + " ms.");
    System.out.println("Size:" + stats.bytes);
    System.out.println("Total trigrams: " + stats.allTrigrams.size());
    System.out.println("Max per file: " + stats.maxtrigrams);

    System.out.println("Sample query 1: " + lookup(stats.filesMap, "trigram"));
    System.out.println("Sample query 2: " + lookup(stats.filesMap, "some text that most probably doesn't exist"));
    System.out.println("Sample query 3: " + lookup(stats.filesMap, "ProfilingUtil.captureCPUSnapshot();"));

    System.out.println("Stop words:");

    listWithBarier(stats, stats.files * 2 / 4);
    listWithBarier(stats, stats.files * 3 / 4);
    listWithBarier(stats, stats.files * 4 / 5);
  }

  private static void listWithBarier(Stats stats, final int barrier) {
    final int[] stopCount = {0};
    stats.filesMap.forEachEntry(new TIntObjectProcedure<List<File>>() {
      public boolean execute(int a, List<File> b) {
        if (b.size() > barrier) {
          System.out.println(a);
          stopCount[0]++;
        }
        return true;
      }
    });

    System.out.println("Total of " + stopCount[0]);
  }

  private static void walk(File root, Stats stats) throws IOException {
    String name = root.getName();
    if (root.isDirectory()) {
      if (name.startsWith(".") || name.equals("out")) return;
      System.out.println("Lexing in " + root.getPath());
      for (File file : root.listFiles()) {
        walk(file, stats);
      }
    }
    else {
      String ext = FileUtilRt.getExtension(name);
      if (!allowedExtension.contains(ext)) return;
      if (root.length() > 100 * 1024) return;

      stats.extensions.add(ext);
      lex(root, stats);
    }
  }

  private static void lex(File root, Stats stats) throws IOException {
    stats.files++;
    BufferedReader reader = new BufferedReader(new FileReader(root));
    String s;
    StringBuilder buf = new StringBuilder();
    while ((s = reader.readLine()) != null) {
      stats.lines++;
      buf.append(s).append("\n");
    }

    stats.bytes += buf.length();

    long start = System.nanoTime();
    TIntHashSet localTrigrams = lexText(buf);
    stats.time += System.nanoTime() - start;
    stats.maxtrigrams = Math.max(stats.maxtrigrams, localTrigrams.size());
    int[] graphs = localTrigrams.toArray();
    stats.allTrigrams.addAll(graphs);
    for (int graph : graphs) {
      List<File> list = stats.filesMap.get(graph);
      if (list == null) {
        list = new ArrayList<File>();
        stats.filesMap.put(graph, list);
      }
      list.add(root);
    }
  }

  private static final Set<String> allowedExtension = new THashSet<String>(
    Arrays.asList("iml", "xml", "java", "html", "bat", "policy", "properties", "sh", "dtd", "ipr", "txt", "plist", "form", "xsl", "css",
                  "jsp", "jspx", "xhtml", "tld", "htm", "tag", "jspf", "js", "ft", "xsd", "xls", "rb", "php", "ftl", "c", "y", "erb", "rjs",
                  "rhtml", "sql", "cfml", "groovy", "text", "gsp", "h", "cc", "cpp", "wsdl"), FileUtil.PATH_HASHING_STRATEGY);

  private static Collection<File> lookup(TIntObjectHashMap<List<File>> trigramsDatabase, String query) {
    final Set<File> result = new HashSet<File>();
    int[] graphs = TrigramBuilder.buildTrigram(query).toArray();
    boolean first = true;

    for (int graph : graphs) {
      if (first) {
        result.addAll(trigramsDatabase.get(graph));
        first = false;
      }
      else {
        result.retainAll(trigramsDatabase.get(graph));
      }
    }

    return result;
  }


  private static TIntHashSet lexText(StringBuilder buf) {
    return TrigramBuilder.buildTrigram(buf);
  }

  private static class Stats {
    public int files;
    public int lines;
    public int maxtrigrams;
    public long time;
    public long bytes;
    public final TIntHashSet allTrigrams = new TIntHashSet();
    public final TIntObjectHashMap<List<File>> filesMap = new TIntObjectHashMap<List<File>>();
    public final Set<String> extensions = new HashSet<String>();
  }

}
