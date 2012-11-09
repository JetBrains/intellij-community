Map<String, Integer> map=["2":2, "3":3];
map.each{
  key, value->
  key.codePointA<caret>
}