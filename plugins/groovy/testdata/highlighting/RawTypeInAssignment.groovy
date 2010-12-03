Map<Integer, Double> map3
map3 = [].collectEntries {}
int map4 = <warning descr="Cannot assign 'Map<K,V>' to 'int'">[].collectEntries {}</warning>
