Integer[] array=[1, 2,3]
array.inject(4){
  value, item->
  item.byteValue()<caret>
}