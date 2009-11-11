Map map=new HashMap();
map.add("abc", "abc");
Date s=<warning descr="Cannot assign 'Object' to 'Date'">map["abc"]</warning>;