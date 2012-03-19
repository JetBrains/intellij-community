class Pair {}
List<Pair> otherPairs = new ArrayList<Pair>();
List<Pair> pairs = otherPairs.findAll({it != null})
List<Date> pairs2 = <warning descr="Cannot assign 'ArrayList<Pair>' to 'List<Date>'">otherPairs.findAll({it != null})</warning>