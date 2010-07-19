class Pair {}
List<Pair> otherPairs = new ArrayList<Pair>();
List<Pair> pairs = otherPairs.findAll({it != null})
List<Date> pairs2 = <warning descr="Cannot assign 'Collection<Pair>' to 'List<Date>'">otherPairs.findAll({it != null})</warning> 