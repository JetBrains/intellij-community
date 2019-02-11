class Pair {}
List<Pair> otherPairs = new ArrayList<Pair>();
List<Pair> pairs = otherPairs.findAll(it -> {it != null})
List<Date> <warning descr="Cannot assign 'List<Object>' to 'List<Date>'">pairs2</warning> = otherPairs.findAll(it->{it != null})