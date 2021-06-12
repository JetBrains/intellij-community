class Simple implements Comparable<Simple> {

  public int compareTo(Simple other) {
    return 0;
  }

    @Override
    public boolean equals(Object o) {
        return o instanceof Simple && compareTo((Simple) o) == 0;
    }
}