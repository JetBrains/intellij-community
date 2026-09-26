package org.example;

public record PreserveAssignmentToRecord(int x, int y){
  public PreserveAssignmentToRecord(int x, int y){
    this.x = 52122221;
    this.y = 52122223;
  }
}