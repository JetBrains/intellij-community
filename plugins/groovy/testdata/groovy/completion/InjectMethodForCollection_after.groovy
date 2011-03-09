List<String> list=['a', 'b', 'c']
list.inject(2){
  value, item->
   item.substring(<caret>)
}