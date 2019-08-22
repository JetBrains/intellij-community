package com.intellij.jps.cache.client;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ArtifactoryQueryBuilder {
  private Name repositorySubQuery;
  private Name pathSubQuery;
  private Sort sortSubQuery;
  private int limit = 1000;

  ArtifactoryQueryBuilder findRepository(Name subQuery) {
    repositorySubQuery = subQuery;
    return this;
  }

  ArtifactoryQueryBuilder withPath(Name subQuery) {
    pathSubQuery = subQuery;
    return this;
  }

  ArtifactoryQueryBuilder sortBy(Sort subQuery) {
    sortSubQuery = subQuery;
    return this;
  }

  ArtifactoryQueryBuilder limit(int limit) {
    this.limit = limit;
    return this;
  }

  String build() {
    StringBuilder resultQuery = new StringBuilder();
    resultQuery.append("items.find({");
    if (repositorySubQuery != null) {
      resultQuery.append("\"repo\":{").append(repositorySubQuery.getSubQuery()).append("}");
    }
    if (pathSubQuery != null) {
      resultQuery.append(", \"path\":{").append(pathSubQuery.getSubQuery()).append("}");
    }
    resultQuery.append("})");
    if (sortSubQuery != null) {
      resultQuery.append(".sort({").append(sortSubQuery.getSubQuery()).append("})");
    }
    resultQuery.append(".limit(").append(limit).append(")");
    return resultQuery.toString();
  }

  static class Sort {
    private final String sortType;
    private final String[] sortFields;

    private Sort(String sortType, String... sortFields) {
      this.sortType = sortType;
      this.sortFields = sortFields;
    }

    private String getSubQuery() {
      String joinedFields = Arrays.stream(sortFields).map(field -> "\"" + field + "\"").collect(Collectors.joining(", "));
      return "\"$" + sortType + "\": [" + joinedFields + "]";
    }

    static Sort asc(String... sortFields) {
      return createSortSubQuery("asc", sortFields);
    }

    static Sort desc(String... sortFields) {
      return createSortSubQuery("desc", sortFields);
    }

    private static Sort createSortSubQuery(String sortType, String... sortFields) {
      return new Sort(sortType, sortFields);
    }
  }

  static class Name {
    private final String comparisonOperator;
    private final String name;

    private Name(String comparisonOperator, String name) {
      this.comparisonOperator = comparisonOperator;
      this.name = name;
    }

    private String getSubQuery() {
      return "\"$" + comparisonOperator + "\": \"" + name + "\"";
    }

    static Name eq(String name){
      return createNameSubQuery("eq", name);
    }

    static Name ne(String name){
      return createNameSubQuery("ne", name);
    }

    static Name gt(String name){
      return createNameSubQuery("gt", name);
    }

    static Name lt(String name){
      return createNameSubQuery("lt", name);
    }

    static Name match(String name){
      return createNameSubQuery("match", name);
    }

    static Name nmatch(String name){
      return createNameSubQuery("nmatch", name);
    }

    private static Name createNameSubQuery(String comparisonOperator, String name) {
      return new Name(comparisonOperator, name);
    }
  }
}
