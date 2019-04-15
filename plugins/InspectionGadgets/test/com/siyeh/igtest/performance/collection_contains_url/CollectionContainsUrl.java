package com.siyeh.igtest.performance.collection_contains_url;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CollectionContainsUrl {
  public void foo() throws MalformedURLException {
    Map<URL, String> <warning descr="Map 'hm1' may contain URL objects">hm1</warning> = new HashMap<>();
    Map<String, URL> hm2 = new HashMap<>();
    Set<URL> <warning descr="Set 'set1' may contain URL objects">set1</warning> = new HashSet<>();

    URL url = new URL("https://jetbrains.team");
    Map <warning descr="Map 'hm3' may contain URL objects">hm3</warning> = new HashMap();
    hm3.put(url, "link");

    Map hm4 = new HashMap();
    hm4.put("link", url);

    Set <warning descr="Set 'set2' may contain URL objects">set2</warning> = new HashSet();
    set2.add(url);

  }
}