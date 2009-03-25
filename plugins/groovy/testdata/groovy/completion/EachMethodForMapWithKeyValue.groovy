Map<String, Intger> map=["2":2, "3":3];
map.each{
  key, value->
  key.codePoint<caret>
}