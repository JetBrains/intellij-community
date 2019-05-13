class Pair {}
List<Pair> otherPairs = new ArrayList<Pair>();
List<Pair> pairs = otherPairs.findAll({it != null})
List<Date> <warning descr="Cannot assign 'ArrayList<Pair>' to 'List<Date>'">pairs2</warning> = otherPairs.findAll({it != null})