Map<String, Integer> map=["1":2, "3":4]
map.inject(2){
  value, entry->
  entry.getK<caret>
}